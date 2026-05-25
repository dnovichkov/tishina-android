package ru.dmdp.tishina.feature.history

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.feature.history.ui.BulkDeleteConfirmDialogCancelTestTag
import ru.dmdp.tishina.feature.history.ui.BulkDeleteConfirmDialogConfirmTestTag
import ru.dmdp.tishina.feature.history.ui.HistoryItemCardTestTagPrefix

/**
 * FR-12 multi-select UI flow tests.
 *
 * Drives the testable [HistoryScreenContent] under Robolectric. Events captured into a
 * recorded `mutableListOf<HistoryUiEvent>` so we can assert the UI's contract with the
 * ViewModel without standing up a real Hilt graph.
 *
 * `createAndroidComposeRule<ComponentActivity>()` (not the plain `createComposeRule()`)
 * is required because the BackHandler composable only registers a callback when there's
 * a hosting `OnBackPressedDispatcher` — that's provided by `ComponentActivity`. Without
 * it the back-press test would silently no-op.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class HistoryScreenSelectionComposeUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun sampleSummary(id: Long, title: String = "item-$id") = MeasurementSummary(
        id = id,
        createdAtEpochMs = 1_727_827_200_000L - id * 1_000L,
        durationMs = 5_000L,
        avgDb = 60f,
        minDb = 50f,
        maxDb = 70f,
        title = title,
        note = null,
        sparklinePreview = listOf(55f, 60f, 65f, 62f),
    )

    @Test
    fun long_press_on_card_emits_EnterSelectionMode_with_card_id() {
        val received = mutableListOf<HistoryUiEvent>()
        val items = listOf(sampleSummary(1L), sampleSummary(2L))
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                HistoryScreenContent(
                    state = HistoryUiState(items = items, loading = false),
                    onEvent = { received += it },
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(HistoryItemCardTestTagPrefix + "2")
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        assertEquals(
            listOf<HistoryUiEvent>(HistoryUiEvent.EnterSelectionMode(initialId = 2L)),
            received,
        )
    }

    @Test
    fun tap_in_selection_mode_emits_ToggleSelection_for_card_id() {
        val received = mutableListOf<HistoryUiEvent>()
        val items = listOf(sampleSummary(1L), sampleSummary(2L), sampleSummary(3L))
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = items,
                        loading = false,
                        selectionMode = true,
                        selectedIds = setOf(1L),
                    ),
                    onEvent = { received += it },
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(HistoryItemCardTestTagPrefix + "3").performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            listOf<HistoryUiEvent>(HistoryUiEvent.ToggleSelection(id = 3L)),
            received,
        )
    }

    @Test
    fun tap_in_selection_mode_does_not_navigate_to_detail() {
        var navigated: Long? = null
        val items = listOf(sampleSummary(1L), sampleSummary(2L))
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = items,
                        loading = false,
                        selectionMode = true,
                        selectedIds = setOf(1L),
                    ),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = { navigated = it },
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(HistoryItemCardTestTagPrefix + "2").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Tap in selection mode must not navigate to Detail", null, navigated)
    }

    @Test
    fun selection_top_bar_select_all_emits_SelectAll_event() {
        val received = mutableListOf<HistoryUiEvent>()
        val items = listOf(sampleSummary(1L), sampleSummary(2L), sampleSummary(3L))
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = items,
                        loading = false,
                        selectionMode = true,
                        selectedIds = setOf(1L),
                    ),
                    onEvent = { received += it },
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Select all").performClick()
        composeTestRule.waitForIdle()
        assertTrue(received.contains(HistoryUiEvent.SelectAll))
    }

    @Test
    fun selection_top_bar_cancel_emits_ExitSelectionMode() {
        val received = mutableListOf<HistoryUiEvent>()
        val items = listOf(sampleSummary(1L), sampleSummary(2L))
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = items,
                        loading = false,
                        selectionMode = true,
                        selectedIds = setOf(1L),
                    ),
                    onEvent = { received += it },
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Cancel selection").performClick()
        composeTestRule.waitForIdle()
        assertTrue(received.contains(HistoryUiEvent.ExitSelectionMode))
    }

    @Test
    fun delete_button_opens_confirm_dialog_and_confirm_emits_BulkDeleteRequested() {
        val received = mutableListOf<HistoryUiEvent>()
        val items = listOf(sampleSummary(1L), sampleSummary(2L), sampleSummary(3L))
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = items,
                        loading = false,
                        selectionMode = true,
                        selectedIds = setOf(1L, 2L),
                    ),
                    onEvent = { received += it },
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Delete selected measurements").performClick()
        composeTestRule.waitForIdle()
        // Confirm dialog must appear with the pluralized title.
        composeTestRule.onNodeWithText("Delete 2 measurements?").assertIsDisplayed()
        composeTestRule.onNodeWithTag(BulkDeleteConfirmDialogConfirmTestTag).performClick()
        composeTestRule.waitForIdle()
        assertTrue(received.contains(HistoryUiEvent.BulkDeleteRequested))
    }

    @Test
    fun confirm_dialog_cancel_does_not_emit_BulkDeleteRequested() {
        val received = mutableListOf<HistoryUiEvent>()
        val items = listOf(sampleSummary(1L), sampleSummary(2L))
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = items,
                        loading = false,
                        selectionMode = true,
                        selectedIds = setOf(1L),
                    ),
                    onEvent = { received += it },
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Delete selected measurements").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BulkDeleteConfirmDialogCancelTestTag).performClick()
        composeTestRule.waitForIdle()
        assertEquals("Cancel must not emit BulkDeleteRequested", false, received.contains(HistoryUiEvent.BulkDeleteRequested))
    }

    @Test
    fun pending_bulk_undo_count_drives_bulk_undo_snackbar() {
        // Mirror of the single-Undo rotation test: the bulk Undo affordance must re-attach
        // when the screen recomposes with the same non-zero pendingBulkUndoCount.
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = listOf(sampleSummary(99L)),
                        loading = false,
                        pendingBulkUndoCount = 3,
                    ),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithText("3 measurements deleted").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun bulk_undo_action_invokes_BulkUndoConfirmed() {
        val received = mutableListOf<HistoryUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = listOf(sampleSummary(99L)),
                        loading = false,
                        pendingBulkUndoCount = 2,
                    ),
                    onEvent = { received += it },
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf<HistoryUiEvent>(HistoryUiEvent.BulkUndoConfirmed), received)
    }

    @Test
    fun back_press_in_selection_mode_emits_ExitSelectionMode_instead_of_unwinding() {
        val received = mutableListOf<HistoryUiEvent>()
        val items = listOf(sampleSummary(1L), sampleSummary(2L))
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = items,
                        loading = false,
                        selectionMode = true,
                        selectedIds = setOf(1L),
                    ),
                    onEvent = { received += it },
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        composeTestRule.waitForIdle()
        assertTrue(received.contains(HistoryUiEvent.ExitSelectionMode))
    }
}
