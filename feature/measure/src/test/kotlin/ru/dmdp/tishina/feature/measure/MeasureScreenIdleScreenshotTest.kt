package ru.dmdp.tishina.feature.measure

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme

/**
 * Full-screen baseline for the freshly-launched Idle state — empty stats, zero readout,
 * permission not yet granted. Drives [MeasureScreenContent] directly with a hand-built
 * [MeasureUiState] so the test does not require Hilt or a real Activity.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class MeasureScreenIdleScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun idle_light() = capture(dark = false, name = "idle_light")

    @Test
    fun idle_dark() = capture(dark = true, name = "idle_dark")

    private fun capture(dark: Boolean, name: String) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                MeasureScreenContent(
                    state = MeasureUiState(
                        phase = MeasurementPhase.Idle,
                        permissionState = PermissionState.Unknown,
                    ),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    showRationale = false,
                    onRationaleConfirm = {},
                    onRationaleDismiss = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/MeasureScreenIdleScreenshotTest_$name.png",
        )
    }
}
