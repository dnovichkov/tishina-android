package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme

/**
 * Drives the FR-6 Save form through user input + click handlers.
 *
 * Uses [MeasureSaveDialogContent] (the Window-less variant) for the same reason as the screenshot
 * suite: Material 3 `AlertDialog`'s sub-Window plus `OutlinedTextField` focus transitions never
 * reach Compose idle under Robolectric and Compose UI Test, so `performTextInput` deadlocks with
 * `AppNotIdleException`. The Surface-based content tree settles normally.
 *
 * `LocalInspectionMode = true` extra-disables the caret-blink coroutine that lives inside the
 * focused TextField.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class MeasureSaveDialogValidationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setDialogContent(
        onConfirm: (String?, String?) -> Unit = { _, _ -> },
        onDismiss: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                TishinaTheme(dynamicColors = false) {
                    MeasureSaveDialogContent(onConfirm = onConfirm, onDismiss = onDismiss)
                }
            }
        }
    }

    @Test
    fun confirm_button_disabled_when_note_exceeds_200_chars() {
        setDialogContent()
        composeTestRule.onNodeWithTag(MeasureSaveDialogNoteFieldTestTag)
            .performTextInput("n".repeat(201))
        composeTestRule.onNodeWithTag(MeasureSaveDialogConfirmTestTag).assertIsNotEnabled()
    }

    @Test
    fun confirm_button_disabled_when_title_exceeds_80_chars() {
        setDialogContent()
        composeTestRule.onNodeWithTag(MeasureSaveDialogTitleFieldTestTag)
            .performTextInput("a".repeat(81))
        composeTestRule.onNodeWithTag(MeasureSaveDialogConfirmTestTag).assertIsNotEnabled()
    }

    @Test
    fun confirm_button_enabled_at_max_lengths() {
        setDialogContent()
        composeTestRule.onNodeWithTag(MeasureSaveDialogTitleFieldTestTag)
            .performTextInput("a".repeat(80))
        composeTestRule.onNodeWithTag(MeasureSaveDialogNoteFieldTestTag)
            .performTextInput("n".repeat(200))
        composeTestRule.onNodeWithTag(MeasureSaveDialogConfirmTestTag).assertIsEnabled()
    }

    @Test
    fun confirm_click_invokes_onConfirm_with_typed_values() {
        var capturedTitle: String? = "<unset>"
        var capturedNote: String? = "<unset>"
        setDialogContent(
            onConfirm = { t, n ->
                capturedTitle = t
                capturedNote = n
            },
        )
        composeTestRule.onNodeWithTag(MeasureSaveDialogTitleFieldTestTag)
            .performTextReplacement("Bedroom")
        composeTestRule.onNodeWithTag(MeasureSaveDialogNoteFieldTestTag)
            .performTextReplacement("evening")
        composeTestRule.onNodeWithTag(MeasureSaveDialogConfirmTestTag).performClick()
        assertEquals("Bedroom", capturedTitle)
        assertEquals("evening", capturedNote)
    }

    @Test
    fun cancel_click_invokes_onDismiss() {
        var dismissed = false
        setDialogContent(onDismiss = { dismissed = true })
        composeTestRule.onNodeWithTag(MeasureSaveDialogCancelTestTag).performClick()
        assertTrue("expected onDismiss invoked on cancel click", dismissed)
    }

    @Test
    fun empty_title_and_note_are_propagated_as_null_to_onConfirm() {
        var capturedTitle: String? = "untouched"
        var capturedNote: String? = "untouched"
        setDialogContent(
            onConfirm = { t, n ->
                capturedTitle = t
                capturedNote = n
            },
        )
        composeTestRule.onNodeWithTag(MeasureSaveDialogConfirmTestTag).performClick()
        assertEquals(null, capturedTitle)
        assertEquals(null, capturedNote)
    }
}
