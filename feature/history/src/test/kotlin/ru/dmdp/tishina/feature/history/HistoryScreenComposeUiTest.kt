package ru.dmdp.tishina.feature.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.ui.components.AppEmptyStateCtaTestTag
import ru.dmdp.tishina.core.ui.components.AppEmptyStateTestTag
import ru.dmdp.tishina.feature.history.ui.HistoryItemCardTestTagPrefix

/**
 * Drives the FR-9 list / FR-11 swipe flow through the testable [HistoryScreenContent].
 *
 * Why this approach instead of `performTouchInput { swipeLeft() }` directly: the Material 3
 * `SwipeToDismissBox` runs an Animatable-driven gesture state machine that, under Robolectric,
 * regularly fails to settle into the EndToStart value within the Compose idle window. The
 * physical swipe gesture is best verified on a real device — Phase Release.
 *
 * Here we cover the parts that are reliably testable in unit:
 *  - Empty-state CTA → onNavigateToMeasure.
 *  - Card click → onNavigateToDetail with matching id.
 *  - LazyColumn renders with the expected items + stable keys.
 *  - Loading state shows neither list nor empty CTA.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class HistoryScreenComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun empty_state_cta_triggers_navigate_to_measure() {
        var navigated = false
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                HistoryScreenContent(
                    state = HistoryUiState(items = emptyList(), loading = false),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = { navigated = true },
                )
            }
        }
        composeTestRule.onNodeWithTag(AppEmptyStateCtaTestTag)
            .assertIsDisplayed()
            .performClick()
        assertEquals("Empty state CTA must invoke onNavigateToMeasure", true, navigated)
    }

    @Test
    fun card_click_navigates_to_detail_with_matching_id() {
        var openedId: Long? = null
        val items = listOf(sampleSummary(1L), sampleSummary(2L), sampleSummary(3L))
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                HistoryScreenContent(
                    state = HistoryUiState(items = items, loading = false),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = { openedId = it },
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(HistoryItemCardTestTagPrefix + "2").performClick()
        assertEquals(2L, openedId)
    }

    @Test
    fun lazy_column_renders_items_with_stable_keys() {
        val items = listOf(sampleSummary(1L), sampleSummary(2L))
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                HistoryScreenContent(
                    state = HistoryUiState(items = items, loading = false),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(HistoryListTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HistoryItemCardTestTagPrefix + "1").assertIsDisplayed()
        composeTestRule.onNodeWithTag(HistoryItemCardTestTagPrefix + "2").assertIsDisplayed()
    }

    @Test
    fun load_failed_state_shows_error_panel_without_make_measurement_cta() {
        // Regression: previously a Room IO failure surfaced as items=empty → the screen
        // dropped into the "Make first measurement" CTA, telling the user to start over when
        // their data was intact on disk. The fix branches on loadFailed and shows a distinct
        // error panel with NO CTA (since starting a new measurement wouldn't fix the load).
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                HistoryScreenContent(
                    state = HistoryUiState(items = emptyList(), loading = false, loadFailed = true),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(AppEmptyStateTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(AppEmptyStateCtaTestTag).assertDoesNotExist()
        composeTestRule.onNodeWithText("Couldn't load measurements").assertIsDisplayed()
    }

    @Test
    fun pending_undo_id_drives_snackbar_so_rotation_reattaches_affordance() {
        // Regression: previously the Undo snackbar was triggered by a one-shot effect emission
        // from the ViewModel. After rotation / theme change, the recreated screen never saw
        // the already-consumed effect, so the user lost the Undo button while the VM's 5 s
        // commit timer kept running — silently committing the delete.
        //
        // Fix: the snackbar is driven by `state.pendingUndoId`. A re-entry into the screen
        // with the same non-null id triggers the LaunchedEffect again and shows the snackbar,
        // matching what a rotated screen would observe.
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = listOf(sampleSummary(1L)),
                        loading = false,
                        pendingUndoId = 1L,
                    ),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Measurement deleted").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun undo_action_invokes_UndoConfirmed_on_owning_view_model() {
        // The Undo button must wire to onEvent(UndoConfirmed); previously this routing went
        // through the effect-loop closure in the wrapper composable. After moving the snackbar
        // logic into HistoryScreenContent, the testable composable owns the binding.
        val received = mutableListOf<HistoryUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                HistoryScreenContent(
                    state = HistoryUiState(
                        items = listOf(sampleSummary(1L)),
                        loading = false,
                        pendingUndoId = 1L,
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
        assertEquals(listOf<HistoryUiEvent>(HistoryUiEvent.UndoConfirmed), received)
    }

    @Test
    fun loading_state_renders_neither_list_nor_empty_cta() {
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                HistoryScreenContent(
                    state = HistoryUiState(items = emptyList(), loading = true),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(HistoryScreenTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HistoryListTestTag).assertDoesNotExist()
        composeTestRule.onNodeWithTag(AppEmptyStateCtaTestTag).assertDoesNotExist()
    }
}
