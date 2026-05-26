package ru.dmdp.tishina.feature.history.detail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Phase 6 Task 4 — Roborazzi visual regression for the Detail TopAppBar with the new Share
 * action (FR-10 P1). Baselines capture the action order (Share before Delete) and the icon
 * presence — a layout drift that swaps the order or drops Share would surface as a pixel diff
 * in the PR review.
 *
 * Two scenarios × light/dark = 4 baselines:
 *  - `share_button_visible_*` — Share + Delete both rendered, details loaded.
 *  - `share_button_hidden_loading_*` — `details == null`, only Delete remains in the TopBar
 *    (regression guard against accidentally rendering Share with a dead-tap target).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class DetailShareButtonScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleDetails(): MeasurementDetails = MeasurementDetails(
        summary = MeasurementSummary(
            id = 1L,
            createdAtEpochMs = STABLE_CREATED_AT,
            durationMs = 75_000L,
            avgDb = 60f,
            minDb = 42f,
            maxDb = 78f,
            title = "Bedroom",
            note = "Тихо",
            sparklinePreview = emptyList(),
        ),
        samples = (0 until SAMPLE_COUNT).map { i -> SoundSample(60f, (i * 200L)) },
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 48_000,
    )

    @Test
    fun share_button_visible_light() = capture(detailsLoaded = true, dark = false, name = "share_button_visible_light")

    @Test
    fun share_button_visible_dark() = capture(detailsLoaded = true, dark = true, name = "share_button_visible_dark")

    @Test
    fun share_button_hidden_loading_light() =
        capture(detailsLoaded = false, dark = false, name = "share_button_hidden_loading_light")

    @Test
    fun share_button_hidden_loading_dark() =
        capture(detailsLoaded = false, dark = true, name = "share_button_hidden_loading_dark")

    private fun capture(detailsLoaded: Boolean, dark: Boolean, name: String) {
        val state = DetailUiState(
            details = if (detailsLoaded) sampleDetails() else null,
            noteDraft = if (detailsLoaded) "Тихо" else "",
            editingNote = false,
            loading = !detailsLoaded,
        )
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                DetailScreenContent(
                    state = state,
                    onEvent = {},
                    onNavigateBack = {},
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
        composeTestRule.onRoot().captureSnapshot("DetailShareButtonScreenshotTest_$name")
    }

    private companion object {
        const val STABLE_CREATED_AT = 1_727_827_200_000L
        const val SAMPLE_COUNT = 30
    }
}
