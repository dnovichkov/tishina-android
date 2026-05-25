package ru.dmdp.tishina

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.usecase.ObserveAppSettingsUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeSettingsRepository

/**
 * Verifies that [AppViewModel]:
 *
 *  - publishes the persisted [AppearanceSettings] from the very first
 *    collection (Eagerly start, no flash-of-default theme on cold start),
 *  - re-emits when the underlying repository flips theme mode or dynamic colors,
 *  - exposes `AppearanceSettings()` as the StateFlow's initial value so
 *    `collectAsStateWithLifecycle` never sees `null` on the first frame.
 *
 * `Dispatchers.Main` is swapped to `UnconfinedTestDispatcher` so the
 * Eagerly-started `stateIn` upstream actually pulls its first value
 * synchronously inside the test body.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `appearance starts with persisted theme not default`() = runTest {
        val repository = FakeSettingsRepository(
            initialAppearance = AppearanceSettings(
                themeMode = ThemeMode.Dark,
                dynamicColors = false,
                locale = AppLocale.Russian,
            ),
        )
        val viewModel = AppViewModel(ObserveAppSettingsUseCase(repository))

        val first = viewModel.appearance.first()
        assertEquals(ThemeMode.Dark, first.themeMode)
        assertEquals(false, first.dynamicColors)
        assertEquals(AppLocale.Russian, first.locale)
    }

    @Test
    fun `appearance re-emits when theme mode changes`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = AppViewModel(ObserveAppSettingsUseCase(repository))

        // Sequential .first() reads work because each repository setter
        // updates a MutableStateFlow synchronously, and the
        // UnconfinedTestDispatcher propagates the new value into the
        // stateIn-backed `appearance` flow before .first() returns.
        assertEquals(ThemeMode.System, viewModel.appearance.first().themeMode)

        repository.updateThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, viewModel.appearance.first().themeMode)

        repository.updateThemeMode(ThemeMode.Light)
        assertEquals(ThemeMode.Light, viewModel.appearance.first().themeMode)
    }

    @Test
    fun `appearance re-emits when dynamicColors toggles`() = runTest {
        val repository = FakeSettingsRepository(
            initialAppearance = AppearanceSettings(dynamicColors = true),
        )
        val viewModel = AppViewModel(ObserveAppSettingsUseCase(repository))

        assertEquals(true, viewModel.appearance.first().dynamicColors)
        repository.updateDynamicColors(false)
        assertEquals(false, viewModel.appearance.first().dynamicColors)
    }

    @Test
    fun `appearance reflects new locale after updateAppLocale`() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = AppViewModel(ObserveAppSettingsUseCase(repository))

        assertEquals(AppLocale.System, viewModel.appearance.first().locale)
        repository.updateAppLocale(AppLocale.English)
        assertEquals(AppLocale.English, viewModel.appearance.first().locale)
    }

    @Test
    fun `StateFlow value with UnconfinedTestDispatcher resolves to persisted appearance`() = runTest {
        // Regression guard: collectAsStateWithLifecycle reads `.value`
        // synchronously on the first frame — under UnconfinedTestDispatcher
        // the Eagerly-started upstream pulls its first emission before
        // construction returns, so `.value` already reflects the persisted
        // preference (no flash-of-default on cold start under this scheduler).
        val repository = FakeSettingsRepository(
            initialAppearance = AppearanceSettings(themeMode = ThemeMode.Dark, dynamicColors = false),
        )
        val viewModel = AppViewModel(ObserveAppSettingsUseCase(repository))
        val initial = viewModel.appearance.value
        assertEquals(ThemeMode.Dark, initial.themeMode)
        assertEquals(false, initial.dynamicColors)
    }
}
