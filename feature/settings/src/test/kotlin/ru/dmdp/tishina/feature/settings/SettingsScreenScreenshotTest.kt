package ru.dmdp.tishina.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Settings screen baselines:
 *  - `setting_default_light/dark` lock the empty-state shell (Task 4 anchors).
 *  - `setting_calibration_15db_*`, `setting_calibration_negative_8db_*`,
 *    `setting_calibration_at_max_*` lock FR-14 slider variants (Task 5).
 *  - `setting_time_weighting_slow_selected_*` locks the FR-16 chip group with
 *    Slow selected (Task 5).
 *
 * Tasks 6 / 7 will extend with dark-theme-selected and English-locale snapshots.
 *
 * Each variant captures the full `SettingsScreenContent` host (not just the
 * MeasurementSection) so a regression in any adjacent category — Appearance,
 * Language, About footer — still shows up as a pixel diff.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class SettingsScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun setting_default_light() = capture(
        state = defaultState(),
        dark = false,
        name = "setting_default_light",
    )

    @Test
    fun setting_default_dark() = capture(
        state = defaultState(),
        dark = true,
        name = "setting_default_dark",
    )

    @Test
    fun setting_calibration_15db_light() = capture(
        state = defaultState().copy(calibrationOffsetDb = 15f),
        dark = false,
        name = "setting_calibration_15db_light",
    )

    @Test
    fun setting_calibration_15db_dark() = capture(
        state = defaultState().copy(calibrationOffsetDb = 15f),
        dark = true,
        name = "setting_calibration_15db_dark",
    )

    @Test
    fun setting_calibration_negative_8db_light() = capture(
        state = defaultState().copy(calibrationOffsetDb = -8f),
        dark = false,
        name = "setting_calibration_negative_8db_light",
    )

    @Test
    fun setting_calibration_negative_8db_dark() = capture(
        state = defaultState().copy(calibrationOffsetDb = -8f),
        dark = true,
        name = "setting_calibration_negative_8db_dark",
    )

    @Test
    fun setting_calibration_at_max_light() = capture(
        state = defaultState().copy(calibrationOffsetDb = 20f),
        dark = false,
        name = "setting_calibration_at_max_light",
    )

    @Test
    fun setting_calibration_at_max_dark() = capture(
        state = defaultState().copy(calibrationOffsetDb = 20f),
        dark = true,
        name = "setting_calibration_at_max_dark",
    )

    @Test
    fun setting_time_weighting_slow_selected_light() = capture(
        state = defaultState().copy(timeWeighting = TimeWeighting.SLOW),
        dark = false,
        name = "setting_time_weighting_slow_selected_light",
    )

    @Test
    fun setting_time_weighting_slow_selected_dark() = capture(
        state = defaultState().copy(timeWeighting = TimeWeighting.SLOW),
        dark = true,
        name = "setting_time_weighting_slow_selected_dark",
    )

    @Test
    fun setting_dark_theme_selected_light() = capture(
        state = defaultState().copy(themeMode = ThemeMode.Dark),
        dark = false,
        name = "setting_dark_theme_selected_light",
    )

    @Test
    fun setting_dark_theme_selected_dark() = capture(
        state = defaultState().copy(themeMode = ThemeMode.Dark),
        dark = true,
        name = "setting_dark_theme_selected_dark",
    )

    @Test
    fun setting_dynamic_colors_off_light() = capture(
        state = defaultState().copy(dynamicColors = false),
        dark = false,
        name = "setting_dynamic_colors_off_light",
    )

    @Test
    fun setting_dynamic_colors_off_dark() = capture(
        state = defaultState().copy(dynamicColors = false),
        dark = true,
        name = "setting_dynamic_colors_off_dark",
    )

    // NFR-14: интерфейс должен выдерживать масштабирование шрифта до 200 %.
    // Эти baseline'ы фиксируют, что Settings-экран остаётся читаемым (нет
    // overflow, обрезанных подписей, заходящих друг на друга чипов) при
    // увеличении системного шрифта в два раза.
    @Test
    fun setting_default_font_scale_2x_light() = capture(
        state = defaultState(),
        dark = false,
        name = "setting_default_font_scale_2x_light",
        fontScale = 2.0f,
    )

    @Test
    fun setting_default_font_scale_2x_dark() = capture(
        state = defaultState(),
        dark = true,
        name = "setting_default_font_scale_2x_dark",
        fontScale = 2.0f,
    )

    private fun capture(
        state: SettingsUiState,
        dark: Boolean,
        name: String,
        fontScale: Float = 1.0f,
    ) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                if (fontScale == 1.0f) {
                    Host(state)
                } else {
                    val baseDensity = LocalDensity.current
                    val scaledDensity = Density(
                        density = baseDensity.density,
                        fontScale = fontScale,
                    )
                    CompositionLocalProvider(LocalDensity provides scaledDensity) {
                        Host(state)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureSnapshot("SettingsScreenScreenshotTest_$name")
    }

    @Composable
    private fun Host(state: SettingsUiState) {
        SettingsScreenContent(
            state = state,
            isDynamicColorSupported = true,
            onEvent = {},
            onNavigateBack = {},
            onAboutClick = {},
        )
    }

    private fun defaultState(): SettingsUiState = SettingsUiState(
        calibrationOffsetDb = 0f,
        timeWeighting = TimeWeighting.FAST,
        themeMode = ThemeMode.System,
        dynamicColors = true,
        locale = AppLocale.System,
        loading = false,
    )
}
