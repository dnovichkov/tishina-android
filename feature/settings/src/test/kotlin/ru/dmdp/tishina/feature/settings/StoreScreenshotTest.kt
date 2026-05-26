package ru.dmdp.tishina.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * Store-card screenshot for Settings rendered at 1080 × 1920 px. Captured in
 * Russian (class default) and English (method-level @Config override). Calibration
 * is set to +3 dB so the slider anchors visibly to the right — proves to a
 * catalogue browser that the control is interactive.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-rRU-w360dp-h640dp-xxhdpi")
class StoreScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settings_store_default_light_ru() = capture("settings_store_default_light_ru")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-xxhdpi")
    fun settings_store_default_light_en() = capture("settings_store_default_light_en")

    private fun capture(name: String) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        calibrationOffsetDb = 3f,
                        timeWeighting = TimeWeighting.FAST,
                        themeMode = ThemeMode.System,
                        dynamicColors = true,
                        locale = AppLocale.System,
                        loading = false,
                    ),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }
        composeTestRule.onRoot().captureSnapshot("StoreScreenshotTest_$name")
    }
}
