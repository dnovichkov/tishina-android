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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h320dp-xhdpi")
class SplArcGaugeScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun gauge_30dB_light() = capture(db = 30f, dark = false, name = "gauge_30dB_light")

    @Test
    fun gauge_60dB_light() = capture(db = 60f, dark = false, name = "gauge_60dB_light")

    @Test
    fun gauge_85dB_light() = capture(db = 85f, dark = false, name = "gauge_85dB_light")

    @Test
    fun gauge_110dB_light() = capture(db = 110f, dark = false, name = "gauge_110dB_light")

    @Test
    fun gauge_30dB_dark() = capture(db = 30f, dark = true, name = "gauge_30dB_dark")

    @Test
    fun gauge_60dB_dark() = capture(db = 60f, dark = true, name = "gauge_60dB_dark")

    @Test
    fun gauge_85dB_dark() = capture(db = 85f, dark = true, name = "gauge_85dB_dark")

    @Test
    fun gauge_110dB_dark() = capture(db = 110f, dark = true, name = "gauge_110dB_dark")

    private fun capture(db: Float, dark: Boolean, name: String) {
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                SplArcGauge(db = db)
            }
        }
        composeTestRule.onRoot().captureSnapshot("SplArcGaugeScreenshotTest_$name")
    }
}
