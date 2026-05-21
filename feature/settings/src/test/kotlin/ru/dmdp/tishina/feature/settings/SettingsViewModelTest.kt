package ru.dmdp.tishina.feature.settings

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.usecase.ObserveAppSettingsUseCase
import ru.dmdp.tishina.core.domain.usecase.ResetCalibrationUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateAppLocaleUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateCalibrationUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateDynamicColorsUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateThemeModeUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateTimeWeightingUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeSettingsRepository
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule
import ru.dmdp.tishina.core.ui.R as CoreUiR

/**
 * Drives `SettingsViewModel` against [FakeSettingsRepository] to verify the
 * MVI-lite contract: state mirrors DataStore (single source of truth), every
 * event triggers the matching use-case, and out-of-range calibration only
 * emits a snackbar effect without mutating state.
 *
 * Why every test pre-collects `state.first { !it.loading }`: the ViewModel uses
 * `SharingStarted.WhileSubscribed(5000)` so the upstream `combine(config,
 * appearance)` does not run until something subscribes. Without that priming
 * read, `state.value` would stay at the `loading = true` initial value forever
 * because nobody activated the share.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(UnconfinedTestDispatcher())

    private fun viewModel(repository: FakeSettingsRepository = FakeSettingsRepository()): SettingsViewModel =
        SettingsViewModel(
            observeAppSettings = ObserveAppSettingsUseCase(repository),
            updateCalibration = UpdateCalibrationUseCase(repository),
            resetCalibration = ResetCalibrationUseCase(repository),
            updateTimeWeighting = UpdateTimeWeightingUseCase(repository),
            updateThemeMode = UpdateThemeModeUseCase(repository),
            updateDynamicColors = UpdateDynamicColorsUseCase(repository),
            updateAppLocale = UpdateAppLocaleUseCase(repository),
        )

    @Test
    fun `empty repository emits default snapshot`() = runTest {
        val vm = viewModel()

        vm.state.test {
            val state = awaitItem().takeIf { !it.loading } ?: awaitItem()
            assertEquals(0f, state.calibrationOffsetDb)
            assertEquals(TimeWeighting.FAST, state.timeWeighting)
            assertEquals(ThemeMode.System, state.themeMode)
            assertTrue(state.dynamicColors, "dynamicColors must default to true")
            assertEquals(AppLocale.System, state.locale)
            assertEquals(false, state.loading, "loading flips to false after first emission")
        }
    }

    @Test
    fun `seeded repository surfaces those values on first emission`() = runTest {
        val repo = FakeSettingsRepository()
        repo.seed(
            config = MeasurementConfig(calibrationOffsetDb = 5f, timeWeighting = TimeWeighting.SLOW),
            appearance = AppearanceSettings(themeMode = ThemeMode.Dark, dynamicColors = false, locale = AppLocale.English),
        )
        val vm = viewModel(repo)

        vm.state.test {
            val state = awaitItem().takeIf { !it.loading } ?: awaitItem()
            assertEquals(5f, state.calibrationOffsetDb)
            assertEquals(TimeWeighting.SLOW, state.timeWeighting)
            assertEquals(ThemeMode.Dark, state.themeMode)
            assertEquals(false, state.dynamicColors)
            assertEquals(AppLocale.English, state.locale)
        }
    }

    @Test
    fun `ChangeCalibration in range persists value and re-emits state`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)

        vm.state.first { !it.loading } // prime subscription
        vm.onEvent(SettingsUiEvent.ChangeCalibration(3.5f))

        assertEquals(3.5f, repo.currentConfig().calibrationOffsetDb, 0.0001f)
        val updated = vm.state.first { it.calibrationOffsetDb != 0f }
        assertEquals(3.5f, updated.calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `ChangeCalibration out of range emits snackbar effect and keeps previous value`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        vm.effects.test {
            vm.onEvent(SettingsUiEvent.ChangeCalibration(-25f))
            val effect = awaitItem()
            assertTrue(effect is SettingsUiEffect.ShowSnackbar)
            assertEquals(CoreUiR.string.settings_calibration_out_of_range, (effect as SettingsUiEffect.ShowSnackbar).messageRes)
        }

        // Repository was not touched on rejection.
        assertEquals(0f, repo.currentConfig().calibrationOffsetDb, 0.0001f)
        assertEquals(0f, vm.state.value.calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `ChangeCalibration above max emits snackbar effect`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        vm.effects.test {
            vm.onEvent(SettingsUiEvent.ChangeCalibration(20.5f))
            val effect = awaitItem()
            assertTrue(effect is SettingsUiEffect.ShowSnackbar)
        }
        assertEquals(0f, repo.currentConfig().calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `ResetCalibration zeros the offset even when previously set`() = runTest {
        val repo = FakeSettingsRepository()
        repo.seed(config = MeasurementConfig(calibrationOffsetDb = 7f))
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        vm.onEvent(SettingsUiEvent.ResetCalibration)

        assertEquals(0f, repo.currentConfig().calibrationOffsetDb, 0.0001f)
        val updated = vm.state.first { it.calibrationOffsetDb == 0f }
        assertEquals(0f, updated.calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `ChangeTimeWeighting persists the new weighting`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        vm.onEvent(SettingsUiEvent.ChangeTimeWeighting(TimeWeighting.SLOW))

        assertEquals(TimeWeighting.SLOW, repo.currentConfig().timeWeighting)
        val updated = vm.state.first { it.timeWeighting == TimeWeighting.SLOW }
        assertEquals(TimeWeighting.SLOW, updated.timeWeighting)
    }

    @Test
    fun `ChangeThemeMode persists the new theme mode`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        vm.onEvent(SettingsUiEvent.ChangeThemeMode(ThemeMode.Dark))

        assertEquals(ThemeMode.Dark, repo.currentAppearance().themeMode)
        val updated = vm.state.first { it.themeMode == ThemeMode.Dark }
        assertEquals(ThemeMode.Dark, updated.themeMode)
    }

    @Test
    fun `ChangeDynamicColors persists the toggle`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        vm.onEvent(SettingsUiEvent.ChangeDynamicColors(false))

        assertEquals(false, repo.currentAppearance().dynamicColors)
        val updated = vm.state.first { !it.dynamicColors }
        assertEquals(false, updated.dynamicColors)
    }

    @Test
    fun `ChangeAppLocale persists locale and emits ApplyAppLocale effect`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        vm.effects.test {
            vm.onEvent(SettingsUiEvent.ChangeAppLocale(AppLocale.English))
            val effect = awaitItem()
            assertTrue(effect is SettingsUiEffect.ApplyAppLocale)
            assertEquals(AppLocale.English, (effect as SettingsUiEffect.ApplyAppLocale).locale)
        }

        assertEquals(AppLocale.English, repo.currentAppearance().locale)
        val updated = vm.state.first { it.locale == AppLocale.English }
        assertEquals(AppLocale.English, updated.locale)
    }

    @Test
    fun `rapid consecutive calibration changes settle on the last value`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        vm.onEvent(SettingsUiEvent.ChangeCalibration(1f))
        vm.onEvent(SettingsUiEvent.ChangeCalibration(2f))
        vm.onEvent(SettingsUiEvent.ChangeCalibration(3f))

        assertEquals(3f, repo.currentConfig().calibrationOffsetDb, 0.0001f)
        val final = vm.state.first { it.calibrationOffsetDb == 3f }
        assertEquals(3f, final.calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `state reflects external repository update without explicit event`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        // External update simulates DataStore writes coming from another process / surface.
        repo.seed(appearance = AppearanceSettings(themeMode = ThemeMode.Light))

        val updated = vm.state.first { it.themeMode == ThemeMode.Light }
        assertEquals(ThemeMode.Light, updated.themeMode)
    }

    @Test
    fun `calibration above range followed by valid update lands on the valid value`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        // Reject path: state must be untouched.
        vm.effects.test {
            vm.onEvent(SettingsUiEvent.ChangeCalibration(50f))
            assertNotNull(awaitItem())
        }
        assertEquals(0f, vm.state.value.calibrationOffsetDb, 0.0001f)

        // Accept path: state updates.
        vm.onEvent(SettingsUiEvent.ChangeCalibration(2.5f))
        val updated = vm.state.first { it.calibrationOffsetDb == 2.5f }
        assertEquals(2.5f, updated.calibrationOffsetDb, 0.0001f)
    }

    @Test
    fun `effects channel buffers when no subscriber is collecting yet`() = runTest {
        val repo = FakeSettingsRepository()
        val vm = viewModel(repo)
        vm.state.first { !it.loading }

        // Fire-and-collect-later: a buffered Channel must keep the effect until someone
        // collects it, mirroring rotation behavior (UI temporarily not subscribed).
        vm.onEvent(SettingsUiEvent.ChangeAppLocale(AppLocale.Russian))
        vm.onEvent(SettingsUiEvent.ChangeCalibration(-99f))

        vm.effects.test {
            val first = awaitItem()
            val second = awaitItem()
            // ApplyAppLocale and ShowSnackbar both arrive — order matches emission order.
            assertTrue(first is SettingsUiEffect.ApplyAppLocale)
            assertTrue(second is SettingsUiEffect.ShowSnackbar)
        }
    }
}
