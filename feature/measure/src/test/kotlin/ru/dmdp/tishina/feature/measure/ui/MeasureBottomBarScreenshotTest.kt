package ru.dmdp.tishina.feature.measure.ui

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
import ru.dmdp.tishina.feature.measure.MeasurementPhase

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h140dp-xhdpi")
class MeasureBottomBarScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun bottombar_idle_light() = capture(MeasurementPhase.Idle, dark = false, name = "bottombar_idle_light")

    @Test
    fun bottombar_running_light() = capture(MeasurementPhase.Running, dark = false, name = "bottombar_running_light")

    @Test
    fun bottombar_paused_light() = capture(MeasurementPhase.Paused, dark = false, name = "bottombar_paused_light")

    @Test
    fun bottombar_idle_dark() = capture(MeasurementPhase.Idle, dark = true, name = "bottombar_idle_dark")

    @Test
    fun bottombar_running_dark() = capture(MeasurementPhase.Running, dark = true, name = "bottombar_running_dark")

    @Test
    fun bottombar_paused_dark() = capture(MeasurementPhase.Paused, dark = true, name = "bottombar_paused_dark")

    private fun capture(phase: MeasurementPhase, dark: Boolean, name: String) {
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                MeasureBottomBar(
                    phase = phase,
                    onStartPause = {},
                    onReset = {},
                    onSave = {},
                )
            }
        }
        composeTestRule.onRoot().captureSnapshot("MeasureBottomBarScreenshotTest_$name")
    }
}
