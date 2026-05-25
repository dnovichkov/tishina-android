package ru.dmdp.tishina.feature.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.testing.composables.PreviewSheet
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Locks the FR-12 selection visuals:
 *  - selected card → secondaryContainer tint + visible checkmark glyph
 *  - unselected-in-selection-mode card → checkmark "well" reserved (no glyph) so the
 *    layout doesn't reflow when the user toggles selection
 *
 * The reserved well is the part that matters for visual stability — if you don't render
 * a placeholder, half the list jumps left/right while the user is tapping. The baseline
 * locks the "well visible, glyph hidden" state explicitly.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class HistoryItemCardSelectionScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sparkline = listOf(40f, 45f, 50f, 55f, 60f, 65f, 70f, 65f, 60f, 55f)

    private fun sampleSummary(
        id: Long = 1L,
        title: String? = "Спальня",
        note: String? = "22:00, поздний вечер",
    ) = MeasurementSummary(
        id = id,
        createdAtEpochMs = STABLE_CREATED_AT,
        durationMs = 65_000L,
        avgDb = 58.3f,
        minDb = 41f,
        maxDb = 72f,
        title = title,
        note = note,
        sparklinePreview = sparkline,
    )

    @Test
    fun card_selected_light() = capture(
        dark = false,
        name = "card_selected_light",
        summary = sampleSummary(),
        selectionMode = true,
        selected = true,
    )

    @Test
    fun card_selected_dark() = capture(
        dark = true,
        name = "card_selected_dark",
        summary = sampleSummary(),
        selectionMode = true,
        selected = true,
    )

    @Test
    fun card_unselected_in_selection_mode_light() = capture(
        dark = false,
        name = "card_unselected_in_selection_mode_light",
        summary = sampleSummary(),
        selectionMode = true,
        selected = false,
    )

    @Test
    fun card_unselected_in_selection_mode_dark() = capture(
        dark = true,
        name = "card_unselected_in_selection_mode_dark",
        summary = sampleSummary(),
        selectionMode = true,
        selected = false,
    )

    private fun capture(
        dark: Boolean,
        name: String,
        summary: MeasurementSummary,
        selectionMode: Boolean,
        selected: Boolean,
    ) {
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryItemCard(
                        summary = summary,
                        selectionMode = selectionMode,
                        selected = selected,
                    )
                }
            }
        }
        composeTestRule.onRoot().captureSnapshot("HistoryItemCardSelectionScreenshotTest_$name")
    }

    private companion object {
        const val STABLE_CREATED_AT = 1_727_827_200_000L
    }
}
