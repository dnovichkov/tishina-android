package ru.dmdp.tishina.feature.measure

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.testing.rules.captureSnapshot
import kotlin.math.PI
import kotlin.math.sin

/**
 * RuStore / Google Play store-listing screenshots for the Measure feature.
 *
 * Class-level qualifier `ru-rRU-w360dp-h640dp-xxhdpi` resolves to **1080 × 1920 px**
 * = native 9:16 (RuStore-required) with Russian resources. Each scene is captured
 * twice: `_ru` (default) and `_en` (method-level `@Config` override).
 *
 * Output PNGs are published into `app/src/main/store-metadata/{rustore,google-play}/...`
 * by `docs/tools/collect_store_screenshots.py`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-rRU-w360dp-h640dp-xxhdpi")
class StoreScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun measure_store_idle_light_ru() = idleCapture("measure_store_idle_light_ru")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-xxhdpi")
    fun measure_store_idle_light_en() = idleCapture("measure_store_idle_light_en")

    @Test
    fun measure_store_running_light_ru() = runningCapture("measure_store_running_light_ru")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-xxhdpi")
    fun measure_store_running_light_en() = runningCapture("measure_store_running_light_en")

    private fun idleCapture(name: String) {
        renderAndCapture(name) {
            MeasureScreenContent(
                state = MeasureUiState(
                    phase = MeasurementPhase.Idle,
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

    private fun runningCapture(name: String) {
        val samples = (0 until SAMPLE_COUNT).map { i ->
            val t = i.toFloat() / SAMPLE_COUNT.toFloat()
            val db = CENTER_DB + AMPLITUDE_DB * sin(t * 2f * PI.toFloat() * CYCLES)
            SoundSample(db = db, timestampMs = (i * STEP_MS).toLong())
        }
        renderAndCapture(name) {
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

    private fun renderAndCapture(name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                content()
            }
        }
        composeTestRule.onRoot().captureSnapshot("StoreScreenshotTest_$name")
    }

    private companion object {
        const val SAMPLE_COUNT = 60
        const val STEP_MS = 1000L
        const val CENTER_DB = 62.5f
        const val AMPLITUDE_DB = 12.5f
        const val CYCLES = 3f
    }
}
