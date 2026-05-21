package ru.dmdp.tishina.core.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.testing.composables.PreviewSheet
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Calibration slider — 4 thumb positions × 2 themes = 8 baselines. Includes the
 * boundary `+20 dB` snapshot to lock the right-edge layout (text wrap vs.
 * truncation).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SliderPreferenceScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun slider_zero_light() = capture(value = 0f, dark = false, name = "slider_zero_light")

    @Test
    fun slider_zero_dark() = capture(value = 0f, dark = true, name = "slider_zero_dark")

    @Test
    fun slider_plus_15_light() = capture(value = 15f, dark = false, name = "slider_plus_15_light")

    @Test
    fun slider_plus_15_dark() = capture(value = 15f, dark = true, name = "slider_plus_15_dark")

    @Test
    fun slider_minus_15_light() = capture(value = -15f, dark = false, name = "slider_minus_15_light")

    @Test
    fun slider_minus_15_dark() = capture(value = -15f, dark = true, name = "slider_minus_15_dark")

    @Test
    fun slider_at_max_light() = capture(value = 20f, dark = false, name = "slider_at_max_light")

    @Test
    fun slider_at_max_dark() = capture(value = 20f, dark = true, name = "slider_at_max_dark")

    private fun capture(value: Float, dark: Boolean, name: String) {
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                Sample(value)
            }
        }
        composeTestRule.onRoot().captureSnapshot("SliderPreferenceScreenshotTest_$name")
    }

    @Composable
    private fun Sample(value: Float) {
        SliderPreference(
            title = "Calibration",
            value = value,
            onValueChange = {},
            valueRange = -20f..20f,
            steps = 400,
            valueFormatter = { v -> "%+.1f dB".format(v) },
            onResetClick = {},
            description = "Compare with a reference meter.",
            valueContentDescription = { v -> "Slider, current value %+.1f dB".format(v) },
        )
    }
}
