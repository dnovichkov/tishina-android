package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.testing.composables.PreviewSheet

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h120dp-xhdpi")
class SplStatsRowScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun stats_min40_avg65_max90_light() = capture(dark = false, name = "stats_min40_avg65_max90_light")

    @Test
    fun stats_min40_avg65_max90_dark() = capture(dark = true, name = "stats_min40_avg65_max90_dark")

    @Test
    fun stats_empty_light() = capture(empty = true, dark = false, name = "stats_empty_light")

    @Test
    fun stats_empty_dark() = capture(empty = true, dark = true, name = "stats_empty_dark")

    private fun capture(dark: Boolean, name: String, empty: Boolean = false) {
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                if (empty) {
                    SplStatsRow(
                        min = Float.POSITIVE_INFINITY,
                        avg = 0f,
                        max = Float.NEGATIVE_INFINITY,
                    )
                } else {
                    SplStatsRow(min = 40f, avg = 65f, max = 90f)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/SplStatsRowScreenshotTest_$name.png",
        )
    }
}
