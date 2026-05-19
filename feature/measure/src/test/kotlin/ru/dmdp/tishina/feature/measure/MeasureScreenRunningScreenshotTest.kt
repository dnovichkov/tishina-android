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
import ru.dmdp.tishina.core.domain.model.SoundSample
import kotlin.math.PI
import kotlin.math.sin

/**
 * Active-measurement baseline. Synthesises a 60-sample wave between ~50 and ~75 dB so the
 * chart shape is deterministic, sets a 1m23s duration and stats consistent with the wave,
 * and renders both light + dark variants.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class MeasureScreenRunningScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun running_light() = capture(dark = false, name = "running_light")

    @Test
    fun running_dark() = capture(dark = true, name = "running_dark")

    private fun capture(dark: Boolean, name: String) {
        val samples = (0 until SAMPLE_COUNT).map { i ->
            val t = i.toFloat() / SAMPLE_COUNT.toFloat()
            val db = CENTER_DB + AMPLITUDE_DB * sin(t * 2f * PI.toFloat() * CYCLES)
            SoundSample(db = db, timestampMs = (i * STEP_MS).toLong())
        }
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                MeasureScreenContent(
                    state = MeasureUiState(
                        current = 65f,
                        min = 50f,
                        avg = 62.4f,
                        max = 75f,
                        durationMs = 83_000L,
                        recent = samples,
                        phase = MeasurementPhase.Running,
                        permissionState = PermissionState.Granted,
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
            "src/test/snapshots/MeasureScreenRunningScreenshotTest_$name.png",
        )
    }

    private companion object {
        const val SAMPLE_COUNT = 60
        const val STEP_MS = 1000L
        const val CENTER_DB = 62.5f
        const val AMPLITUDE_DB = 12.5f
        const val CYCLES = 3f
    }
}
