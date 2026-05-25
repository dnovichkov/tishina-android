package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Screenshot baselines for the FR-22 accuracy disclaimer sheet. Renders the Window-less
 * [AccuracyDisclaimerSheetContent] variant — Material 3 [androidx.compose.material3.ModalBottomSheet]
 * draws into a separate sub-Window with bottom-up settling animations that never reach
 * Compose idle under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AccuracyDisclaimerBottomSheetScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun disclaimer_sheet_light() = capture(dark = false, name = "disclaimer_sheet_light")

    @Test
    fun disclaimer_sheet_dark() = capture(dark = true, name = "disclaimer_sheet_dark")

    private fun capture(dark: Boolean, name: String) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    Column(modifier = Modifier.padding(top = 240.dp)) {
                        AccuracyDisclaimerSheetContent(onShowFullDisclaimer = {})
                    }
                }
            }
        }
        composeTestRule.onRoot().captureSnapshot("AccuracyDisclaimerBottomSheetScreenshotTest_$name")
    }
}
