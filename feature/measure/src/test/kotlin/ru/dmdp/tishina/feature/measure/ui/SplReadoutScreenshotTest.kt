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

/**
 * Visual regression for [SplReadout]. Four dB tiers × 2 themes = 8 baselines covering
 * "near-silence", "quiet bedroom", "busy office", and "deafening" levels — exercising every
 * branch of the SPL palette except the very-quiet/extreme edges (those are caught by
 * SplLevelPaletteScreenshotTest in :core:designsystem).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h160dp-xhdpi")
class SplReadoutScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun readout_0dB_light() = capture(db = 0f, dark = false, name = "readout_0dB_light")

    @Test
    fun readout_45_5dB_light() = capture(db = 45.5f, dark = false, name = "readout_45_5dB_light")

    @Test
    fun readout_78_3dB_light() = capture(db = 78.3f, dark = false, name = "readout_78_3dB_light")

    @Test
    fun readout_105_7dB_light() = capture(db = 105.7f, dark = false, name = "readout_105_7dB_light")

    @Test
    fun readout_0dB_dark() = capture(db = 0f, dark = true, name = "readout_0dB_dark")

    @Test
    fun readout_45_5dB_dark() = capture(db = 45.5f, dark = true, name = "readout_45_5dB_dark")

    @Test
    fun readout_78_3dB_dark() = capture(db = 78.3f, dark = true, name = "readout_78_3dB_dark")

    @Test
    fun readout_105_7dB_dark() = capture(db = 105.7f, dark = true, name = "readout_105_7dB_dark")

    private fun capture(db: Float, dark: Boolean, name: String) {
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                SplReadout(db = db)
            }
        }
        composeTestRule.onRoot().captureSnapshot("SplReadoutScreenshotTest_$name")
    }
}
