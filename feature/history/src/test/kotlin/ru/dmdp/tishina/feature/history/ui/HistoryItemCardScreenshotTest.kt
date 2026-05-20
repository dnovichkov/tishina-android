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
 * Baselines for the FR-9 card variants we want to lock in:
 *  - card_short_note: typical happy path (1-line note fits).
 *  - card_no_note: only date + stats (the note row collapses).
 *  - card_long_note: ellipsized note across one line so the layout stays one-card-per-row.
 *
 * The createdAtEpochMs picks 1727827200000 (≈ 2024-10-02 in UTC) — a fixed point in the past
 * so the relative-time formatter produces a stable token ("Jan 1, 2024" etc.) instead of
 * "just now" which would shift between recorded baseline and rerun.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class HistoryItemCardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sparkline = listOf(40f, 45f, 50f, 55f, 60f, 65f, 70f, 65f, 60f, 55f, 50f, 48f, 52f, 58f, 64f, 70f, 72f, 70f, 66f, 62f)

    private fun sampleSummary(
        id: Long = 1L,
        title: String? = "Спальня",
        note: String? = "22:00, поздний вечер",
        avgDb: Float = 58.3f,
        minDb: Float = 41f,
        maxDb: Float = 72f,
    ) = MeasurementSummary(
        id = id,
        createdAtEpochMs = STABLE_CREATED_AT,
        durationMs = 65_000L,
        avgDb = avgDb,
        minDb = minDb,
        maxDb = maxDb,
        title = title,
        note = note,
        sparklinePreview = sparkline,
    )

    @Test
    fun card_short_note_light() = capture(dark = false, name = "card_short_note_light", summary = sampleSummary())

    @Test
    fun card_short_note_dark() = capture(dark = true, name = "card_short_note_dark", summary = sampleSummary())

    @Test
    fun card_no_note_light() = capture(
        dark = false,
        name = "card_no_note_light",
        summary = sampleSummary(title = "Двор", note = null, avgDb = 71.2f),
    )

    @Test
    fun card_no_note_dark() = capture(
        dark = true,
        name = "card_no_note_dark",
        summary = sampleSummary(title = "Двор", note = null, avgDb = 71.2f),
    )

    @Test
    fun card_long_note_light() = capture(
        dark = false,
        name = "card_long_note_light",
        summary = sampleSummary(
            title = "Очень-очень длинное название замера для проверки эллипсиса",
            note = "Запись начата вечером, через 20 минут после открытия окон, фонит холодильник и проезжает машина",
        ),
    )

    @Test
    fun card_long_note_dark() = capture(
        dark = true,
        name = "card_long_note_dark",
        summary = sampleSummary(
            title = "Очень-очень длинное название замера для проверки эллипсиса",
            note = "Запись начата вечером, через 20 минут после открытия окон, фонит холодильник и проезжает машина",
        ),
    )

    private fun capture(dark: Boolean, name: String, summary: MeasurementSummary) {
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryItemCard(summary = summary)
                }
            }
        }
        composeTestRule.onRoot().captureSnapshot("HistoryItemCardScreenshotTest_$name")
    }

    private companion object {
        /** 2024-10-02T00:00:00 UTC — a stable past point so DateUtils renders deterministically. */
        const val STABLE_CREATED_AT = 1_727_827_200_000L
    }
}
