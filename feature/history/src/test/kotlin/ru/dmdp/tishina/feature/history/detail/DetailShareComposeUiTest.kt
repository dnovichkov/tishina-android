package ru.dmdp.tishina.feature.history.detail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertHasClickAction
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
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Phase 6 Task 4 — Compose UI test for the Detail share affordance (FR-10 P1).
 *
 * Verifies the TopAppBar action wiring at the testable [DetailScreenContent] level. Why this
 * layer and not the Hilt-aware [DetailScreen]: a true end-to-end exercise of the share Intent
 * (chooser launching, FileProvider URI resolution) belongs in the Phase 6 Task 9
 * instrumentation suite on a real emulator, where `Intent.createChooser` + `startActivity`
 * have a real host Activity. Under Robolectric we assert that:
 *  - the Share icon is rendered with a click action when `state.details` is loaded;
 *  - the icon is *not* rendered while details are null (loading or post-failure redirect);
 *  - tapping it dispatches exactly one [DetailUiEvent.ShareRequested] to the owning VM.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class DetailShareComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleDetails(): MeasurementDetails = MeasurementDetails(
        summary = MeasurementSummary(
            id = 1L,
            createdAtEpochMs = 1_727_827_200_000L,
            durationMs = 75_000L,
            avgDb = 60f,
            minDb = 42f,
            maxDb = 78f,
            title = "Bedroom",
            note = null,
            sparklinePreview = emptyList(),
        ),
        samples = (0 until 30).map { i -> SoundSample(60f, (i * 200L)) },
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 48_000,
    )

    @Test
    fun share_icon_is_visible_when_details_loaded() {
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                DetailScreenContent(
                    state = DetailUiState(details = sampleDetails(), loading = false),
                    onEvent = {},
                    onNavigateBack = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeTestRule.onNodeWithTag(DetailShareIconTestTag)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun share_icon_is_hidden_while_details_are_null() {
        // While `details == null` (loading state) we hide both Share and a dead tap would
        // surface a "no measurement" UX state with no action to take.
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                DetailScreenContent(
                    state = DetailUiState(details = null, loading = true),
                    onEvent = {},
                    onNavigateBack = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeTestRule.onNodeWithTag(DetailShareIconTestTag).assertDoesNotExist()
    }

    @Test
    fun tap_share_emits_ShareRequested_event_exactly_once() {
        val received = mutableListOf<DetailUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                DetailScreenContent(
                    state = DetailUiState(details = sampleDetails(), loading = false),
                    onEvent = { received += it },
                    onNavigateBack = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeTestRule.onNodeWithTag(DetailShareIconTestTag).performClick()
        composeTestRule.waitForIdle()

        // The tap must dispatch exactly one ShareRequested. Defensive: assertEquals with the
        // full list rather than just `received.size == 1` so an accidental ripple-related
        // duplicate (or a stray Delete event from the adjacent icon) shows up loud and clear.
        assertEquals(listOf<DetailUiEvent>(DetailUiEvent.ShareRequested), received)
    }

    @Test
    fun share_and_delete_icons_both_visible_in_topbar() {
        // Regression guard against either icon disappearing under future TopAppBar refactors.
        // We don't pin pixel positions here (theme-dependent + LTR/RTL-dependent); the order
        // assertion lives in the Roborazzi screenshot baseline instead. Both
        // `assertIsDisplayed` calls below DO real work — they fail the test if the testTag
        // node is absent OR has zero bounds — so no tautological `assertTrue(true)` is needed.
        composeTestRule.setContent {
            TishinaTheme(dynamicColors = false) {
                DetailScreenContent(
                    state = DetailUiState(details = sampleDetails(), loading = false),
                    onEvent = {},
                    onNavigateBack = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        composeTestRule.onNodeWithTag(DetailShareIconTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DetailDeleteIconTestTag).assertIsDisplayed()
    }
}
