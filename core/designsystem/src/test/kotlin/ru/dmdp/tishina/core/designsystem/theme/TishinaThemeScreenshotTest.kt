package ru.dmdp.tishina.core.designsystem.theme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi screenshot tests for TishinaTheme — 4 variants:
 *  - light static  (no dynamic colors)
 *  - dark static
 *  - light dynamic (API 31+; @Config(sdk=[31]) for deterministic Robolectric SDK)
 *  - dark dynamic
 *
 * Outputs land in src/test/snapshots/ so baselines are committed to git
 * (Task 9 records them via `./gradlew recordRoborazziDebug`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class TishinaThemeScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun theme_light_static() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                ThemePreviewSheet()
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/TishinaThemeScreenshotTest_theme_light_static.png",
        )
    }

    @Test
    fun theme_dark_static() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = true, dynamicColor = false) {
                ThemePreviewSheet()
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/TishinaThemeScreenshotTest_theme_dark_static.png",
        )
    }

    @Test
    @Config(sdk = [31])
    fun theme_light_dynamic() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = true) {
                ThemePreviewSheet()
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/TishinaThemeScreenshotTest_theme_light_dynamic.png",
        )
    }

    @Test
    @Config(sdk = [31])
    fun theme_dark_dynamic() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = true, dynamicColor = true) {
                ThemePreviewSheet()
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/TishinaThemeScreenshotTest_theme_dark_dynamic.png",
        )
    }
}
