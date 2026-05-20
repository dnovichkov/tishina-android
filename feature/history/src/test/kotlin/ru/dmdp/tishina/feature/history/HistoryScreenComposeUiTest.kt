package ru.dmdp.tishina.feature.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
