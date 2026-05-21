package ru.dmdp.tishina.feature.settings

import androidx.compose.runtime.Composable
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
 * Two anchor baselines for the SettingsScreen scaffold — Tasks 5, 6, 7 will add
 * variant snapshots (calibration values, dark-theme selected, English locale).
 *
 * `setting_default_light/dark` lock the empty-state shell so any structural
 * regression (lost category, swapped order, missing footer) shows up as a
 * pixel diff in PR review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class SettingsScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun setting_default_light() = capture(dark = false, name = "setting_default_light")

    @Test
    fun setting_default_dark() = capture(dark = true, name = "setting_default_dark")

    private fun capture(dark: Boolean, name: String) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                Host()
            }
        }
        composeTestRule.onRoot().captureSnapshot("SettingsScreenScreenshotTest_$name")
    }

    @Composable
    private fun Host() {
        SettingsScreenContent(
            state = SettingsUiState(
                calibrationOffsetDb = 0f,
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
