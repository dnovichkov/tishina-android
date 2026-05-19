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
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.testing.composables.PreviewSheet
import kotlin.math.PI
import kotlin.math.sin

/**
 * Visual regression for [SplLineChart]. Generates 60 synthetic samples oscillating between
 * 40 and 80 dB so the polyline shape is deterministic and conveys "wave-like fluctuation"
 * at a glance — a regression that flattens the curve to a single line would be visually
 * obvious in the diff.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h160dp-xhdpi")
class SplLineChartScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chart_wave_light() = capture(dark = false, name = "chart_wave_light")

    @Test
    fun chart_wave_dark() = capture(dark = true, name = "chart_wave_dark")

    private fun capture(dark: Boolean, name: String) {
        val samples = (0 until SAMPLE_COUNT).map { i ->
            val t = i.toFloat() / SAMPLE_COUNT.toFloat()
            val db = CENTER_DB + AMPLITUDE_DB * sin(t * 2f * PI.toFloat() * CYCLES)
            SoundSample(db = db, timestampMs = (i * STEP_MS).toLong())
        }
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                SplLineChart(samples = samples)
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/SplLineChartScreenshotTest_$name.png",
        )
    }

    private companion object {
        const val SAMPLE_COUNT = 60
        const val STEP_MS = 1000L
        const val CENTER_DB = 60f
        const val AMPLITUDE_DB = 20f
        const val CYCLES = 3f
    }
}
