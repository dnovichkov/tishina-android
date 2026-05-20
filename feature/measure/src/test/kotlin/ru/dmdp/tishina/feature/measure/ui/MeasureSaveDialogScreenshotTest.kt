package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme

/**
 * Screenshot baselines for the FR-6 Save form. We render the Window-less
 * [MeasureSaveDialogContent] (Surface) rather than the production [MeasureSaveDialog]
 * (AlertDialog) — the Material 3 AlertDialog's sub-Window plus `OutlinedTextField`'s focus
 * transitions never reach Compose idle under Robolectric (`AppNotIdleException`). The Surface
 * variant has the exact same visuals minus the dim/scrim layer, which is what we want to
 * pixel-diff anyway.
 *
 * `LocalInspectionMode = true` is set as a belt-and-braces measure to short-circuit any
 * remaining infinite animations inside the text fields (cursor blink in particular).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class MeasureSaveDialogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun empty_light() = capture(title = "", note = "", dark = false, name = "empty_light")

    @Test
    fun empty_dark() = capture(title = "", note = "", dark = true, name = "empty_dark")

    @Test
    fun filled_light() = capture(
        title = "Спальня",
        note = "22:00, поздний вечер",
        dark = false,
        name = "filled_light",
    )

    @Test
    fun filled_dark() = capture(
        title = "Спальня",
        note = "22:00, поздний вечер",
        dark = true,
        name = "filled_dark",
    )

    @Test
    fun note_overflow_light() = capture(
        title = "ok",
        note = "n".repeat(NOTE_OVERFLOW_LENGTH),
        dark = false,
        name = "note_overflow_light",
    )

    @Test
    fun note_overflow_dark() = capture(
        title = "ok",
        note = "n".repeat(NOTE_OVERFLOW_LENGTH),
        dark = true,
        name = "note_overflow_dark",
    )

    private fun capture(title: String, note: String, dark: Boolean, name: String) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                TishinaTheme(darkTheme = dark, dynamicColor = false) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            MeasureSaveDialogContent(
                                initialTitle = title,
                                initialNote = note,
                                onConfirm = { _, _ -> },
                                onDismiss = {},
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/MeasureSaveDialogScreenshotTest_$name.png",
        )
    }

    private companion object {
        const val NOTE_OVERFLOW_LENGTH = 201
    }
}
