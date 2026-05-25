package ru.dmdp.tishina.core.data.settings

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import java.io.File

/**
 * Round-trip & reactivity contract for [SettingsRepositoryImpl] over a real
 * [PreferenceDataStoreFactory] file backing.
 *
 * **Why a real Dispatchers.IO scope, not a TestScope:** DataStore's `.data` flow is
 * served by a long-lived coroutine launched in the `scope` parameter. A `TestScope`
 * uses virtual time and only advances when its own `runTest` block drives it — so a
 * DataStore created against one TestScope and consumed from a different `runTest`
 * block deadlocks: the producer is waiting for its scheduler to run, but the
 * consumer's scheduler doesn't see those tasks. Using `Dispatchers.IO` makes the
 * DataStore work happen on real threads, and `runBlocking` in each test just awaits
 * those real emissions — no virtual-time coordination required.
 *
 * **Why Robolectric at all:** DataStore Preferences itself is a pure-Kotlin library
 * and would happily run in a plain JVM test. The Robolectric runner is here for the
 * `@Config(sdk = TIRAMISU)` consistency with the rest of `:core:data` and to keep
 * the `Application` context available if a future test wants to graduate to the
 * Hilt-injected path.
 *
 * **Windows file-handle cleanup:** DataStore opens an OkioStorage file handle that
 * lives for the duration of `scope`. `cancelAndJoin` in `tearDown` awaits the
 * underlying reader coroutine actually finishing, so the file releases before
 * `TemporaryFolder` deletes it (open handles silently block File.delete on
 * Windows).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class SettingsRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStoreFile: File
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var repository: SettingsRepositoryImpl

    @Before
    fun setUp() {
        dataStoreFile = tempFolder.newFile("test_settings.preferences_pb").apply {
            // PreferenceDataStoreFactory throws if the file already exists empty —
            // delete so the factory writes a fresh one when needed.
            delete()
        }
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = dataStoreScope,
            produceFile = { dataStoreFile },
        )
        repository = SettingsRepositoryImpl(
            dataStore = dataStore,
            ioDispatcher = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        runBlocking { dataStoreScope.coroutineContext.job.cancelAndJoin() }
    }

    @Test
    fun `config emits defaults when DataStore is empty`() = runBlocking {
        val config = repository.config.first()

        assertEquals(MeasurementConfig(), config)
    }

    @Test
    fun `appearance emits defaults when DataStore is empty`() = runBlocking {
        val appearance = repository.appearance.first()

        assertEquals(ThemeMode.System, appearance.themeMode)
        assertTrue("dynamicColors defaults to true (FR-17)", appearance.dynamicColors)
        assertEquals(AppLocale.System, appearance.locale)
    }

    @Test
    fun `updateCalibrationOffset persists value into config Flow`() = runBlocking {
        repository.updateCalibrationOffset(3.5f)

        assertEquals(3.5f, repository.config.first().calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `updateCalibrationOffset second call overwrites first`() = runBlocking {
        repository.updateCalibrationOffset(3.5f)
        repository.updateCalibrationOffset(-1.2f)

        assertEquals(-1.2f, repository.config.first().calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `updateTimeWeighting persists SLOW`() = runBlocking {
        repository.updateTimeWeighting(TimeWeighting.SLOW)

        assertEquals(TimeWeighting.SLOW, repository.config.first().timeWeighting)
    }

    @Test
    fun `updateFrequencyWeighting persists Z`() = runBlocking {
        repository.updateFrequencyWeighting(FrequencyWeighting.Z)

        assertEquals(FrequencyWeighting.Z, repository.config.first().frequencyWeighting)
    }

    @Test
    fun `updateThemeMode persists Dark`() = runBlocking {
        repository.updateThemeMode(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, repository.appearance.first().themeMode)
    }

    @Test
    fun `updateDynamicColors persists false`() = runBlocking {
        repository.updateDynamicColors(false)

        assertEquals(false, repository.appearance.first().dynamicColors)
    }

    @Test
    fun `updateAppLocale persists English`() = runBlocking {
        repository.updateAppLocale(AppLocale.English)

        assertEquals(AppLocale.English, repository.appearance.first().locale)
    }

    @Test
    fun `resetCalibration zeroes a previously set calibration offset`() = runBlocking {
        repository.updateCalibrationOffset(5.0f)
        assertEquals(5.0f, repository.config.first().calibrationOffsetDb, 0.0001f)

        repository.resetCalibration()

        assertEquals(0.0f, repository.config.first().calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `config Flow emits new value after each calibration update`() = runBlocking {
        repository.config.test {
            assertEquals(0.0f, awaitItem().calibrationOffsetDb, 0.0001f)

            repository.updateCalibrationOffset(2.0f)
            assertEquals(2.0f, awaitItem().calibrationOffsetDb, 0.0001f)

            repository.updateCalibrationOffset(-3.0f)
            assertEquals(-3.0f, awaitItem().calibrationOffsetDb, 0.0001f)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `appearance Flow emits new value after each theme update`() = runBlocking {
        repository.appearance.test {
            assertEquals(ThemeMode.System, awaitItem().themeMode)

            repository.updateThemeMode(ThemeMode.Dark)
            assertEquals(ThemeMode.Dark, awaitItem().themeMode)

            repository.updateThemeMode(ThemeMode.Light)
            assertEquals(ThemeMode.Light, awaitItem().themeMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unknown ThemeMode string in preferences falls back to System default`() = runBlocking {
        // Simulate a downgraded/corrupted prefs file that contains an enum variant
        // this version doesn't know — must fall back to default instead of crashing.
        val themeKey = stringPreferencesKey("theme_mode")
        dataStore.edit { prefs -> prefs[themeKey] = "FuturisticPlasma" }

        assertEquals(ThemeMode.System, repository.appearance.first().themeMode)
    }

    @Test
    fun `unknown AppLocale string in preferences falls back to System default`() = runBlocking {
        val localeKey = stringPreferencesKey("app_locale")
        dataStore.edit { prefs -> prefs[localeKey] = "Klingon" }

        assertEquals(AppLocale.System, repository.appearance.first().locale)
    }

    @Test
    fun `unknown TimeWeighting string in preferences falls back to FAST default`() = runBlocking {
        val timeKey = stringPreferencesKey("time_weighting")
        dataStore.edit { prefs -> prefs[timeKey] = "Impulse" }

        assertEquals(TimeWeighting.FAST, repository.config.first().timeWeighting)
    }

    @Test
    fun `corruption handler replaces a damaged file with empty preferences`() = runBlocking {
        // Persist a value so the corruption fallback is observable as a reset back to
        // defaults rather than the stored Dark theme.
        repository.updateThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, repository.appearance.first().themeMode)

        // DataStore 1.1.x's OkioStorage enforces a per-path mutex — cancel() alone is
        // asynchronous, so the next PreferenceDataStoreFactory.create on the same file
        // throws IllegalStateException. cancelAndJoin awaits the underlying reader
        // actually finishing so the handle releases before we rewrite the bytes.
        dataStoreScope.coroutineContext.job.cancelAndJoin()
        dataStoreFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05))

        // Re-open with a fresh scope + corruption handler — the bad bytes should
        // trigger emptyPreferences() rather than propagate CorruptionException.
        val rebuiltScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val rebuiltStore = PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = rebuiltScope,
            produceFile = { dataStoreFile },
        )
        val rebuiltRepository = SettingsRepositoryImpl(
            dataStore = rebuiltStore,
            ioDispatcher = Dispatchers.IO,
        )

        assertEquals(
            "Corruption handler must reset to defaults",
            ThemeMode.System,
            rebuiltRepository.appearance.first().themeMode,
        )
        rebuiltScope.coroutineContext.job.cancelAndJoin()
    }

    @Test
    fun `NaN calibration on disk falls back to 0 instead of poisoning the pipeline`() = runBlocking {
        // Если NaN всё-таки оказался в DataStore (прямая запись мимо use-case'а, даунгрейд),
        // без defensive-read NaN утекает в SPL-калькулятор и каждая последующая сессия пишется
        // как NaN. Проверяем, что чтение возвращает безопасный 0f.
        val calibrationKey = floatPreferencesKey("calibration_offset_db")
        dataStore.edit { prefs -> prefs[calibrationKey] = Float.NaN }

        assertEquals(0f, repository.config.first().calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `out-of-range calibration on disk is clamped to advertised bounds`() = runBlocking {
        // Защита от даунгрейда из будущей версии с более широким диапазоном — клампим к
        // объявленному в `AppearanceSettings.Companion`.
        val calibrationKey = floatPreferencesKey("calibration_offset_db")
        dataStore.edit { prefs -> prefs[calibrationKey] = 50f }

        assertEquals(20f, repository.config.first().calibrationOffsetDb, 0.0001f)

        dataStore.edit { prefs -> prefs[calibrationKey] = -50f }
        assertEquals(-20f, repository.config.first().calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `positive infinity calibration on disk falls back to 0`() = runBlocking {
        val calibrationKey = floatPreferencesKey("calibration_offset_db")
        dataStore.edit { prefs -> prefs[calibrationKey] = Float.POSITIVE_INFINITY }

        assertEquals(0f, repository.config.first().calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `sequential calibration updates apply in submission order`() = runBlocking {
        // DataStore.edit serializes through an internal mutex, so the last submitted
        // value wins regardless of how the suspending edit calls interleave.
        repository.updateCalibrationOffset(1.0f)
        repository.updateCalibrationOffset(2.0f)
        repository.updateCalibrationOffset(3.0f)

        assertEquals(3.0f, repository.config.first().calibrationOffsetDb, 0.0001f)
    }
}
