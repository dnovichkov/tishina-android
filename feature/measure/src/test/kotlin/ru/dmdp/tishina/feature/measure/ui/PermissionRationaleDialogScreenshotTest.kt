package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme

/**
 * Material 3 [androidx.compose.material3.AlertDialog] renders in a separate Compose root
 * window — calling `onRoot()` after `setContent` would error with "Expected 1 node but found
 * 2 (isRoot)". Selecting `onAllNodes(isRoot()).onLast()` deterministically picks the dialog
 * overlay (drawn on top of the underlying surface) so the baseline shows just the dialog —
 * which is what we want to test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PermissionRationaleDialogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rationale_light() = capture(dark = false, name = "rationale_light")

    @Test
    fun rationale_dark() = capture(dark = true, name = "rationale_dark")

    private fun capture(dark: Boolean, name: String) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    PermissionRationaleDialog(onConfirm = {}, onDismiss = {})
                }
            }
        }
        composeTestRule.onAllNodes(isRoot()).onLast().captureRoboImage(
            "src/test/snapshots/PermissionRationaleDialogScreenshotTest_$name.png",
        )
    }
}
