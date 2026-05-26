package ru.dmdp.tishina.feature.history

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
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.testing.rules.captureSnapshot
import ru.dmdp.tishina.feature.history.detail.DetailScreenContent
import ru.dmdp.tishina.feature.history.detail.DetailUiState
import kotlin.math.PI
import kotlin.math.sin

/**
 * RuStore / Google Play store screenshots for History list + Detail. Class-level
 * `ru-rRU-w360dp-h640dp-xxhdpi` → 1080 × 1920 px (9:16). Each scene captured in
 * Russian (`_ru`) and English (`_en`, method-level @Config override).
 *
 * Card content is bilingual on purpose — Russian labels like "Спальня"/"вечер"
 * make the example look localised. English-locale captures still show those
 * labels because they come from fixture data, not resources, which is the
 * realistic state of a Russian user's history list on either locale.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-rRU-w360dp-h640dp-xxhdpi")
class StoreScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sparkline = listOf(40f, 45f, 50f, 55f, 60f, 58f, 56f, 52f, 50f, 48f)

    @Test
    fun history_store_list_light_ru() = listCapture("history_store_list_light_ru", russianFixtures = true)

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-xxhdpi")
    fun history_store_list_light_en() = listCapture("history_store_list_light_en", russianFixtures = false)

    @Test
    fun history_store_detail_light_ru() = detailCapture("history_store_detail_light_ru", russianFixtures = true)

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-xxhdpi")
    fun history_store_detail_light_en() = detailCapture("history_store_detail_light_en", russianFixtures = false)

    private fun listCapture(name: String, russianFixtures: Boolean) {
        val items = if (russianFixtures) russianListFixtures() else englishListFixtures()
        renderAndCapture(name) {
            HistoryScreenContent(
                state = HistoryUiState(items = items, loading = false),
                onEvent = {},
                snackbarHostState = SnackbarHostState(),
                onNavigateToDetail = {},
                onNavigateToMeasure = {},
            )
        }
    }

    private fun detailCapture(name: String, russianFixtures: Boolean) {
        val samples = (0 until SAMPLE_COUNT).map { i ->
            val t = i.toFloat() / SAMPLE_COUNT.toFloat()
            val db = CENTER_DB + AMPLITUDE_DB * sin(t * 2f * PI.toFloat() * CYCLES)
            SoundSample(db = db, timestampMs = (i * STEP_MS).toLong())
        }
        val title = if (russianFixtures) "Спальня" else "Bedroom"
        val note = if (russianFixtures) {
            "Соседи слушают музыку через стену, замер сделан в 22:30."
        } else {
            "Neighbours are playing loud music through the wall, captured at 22:30."
        }
        val state = DetailUiState(
            details = MeasurementDetails(
                summary = MeasurementSummary(
                    id = 1L,
                    createdAtEpochMs = STABLE_CREATED_AT,
                    durationMs = 75_000L,
                    avgDb = 60f,
                    minDb = 42f,
                    maxDb = 78f,
                    title = title,
                    note = note,
                    sparklinePreview = emptyList(),
                ),
                samples = samples,
                weighting = FrequencyWeighting.A,
                timeWeighting = TimeWeighting.FAST,
                calibrationOffsetDb = 0f,
                sampleRateHz = 48_000,
            ),
            noteDraft = note,
            editingNote = false,
            loading = false,
        )
        renderAndCapture(name) {
            DetailScreenContent(
                state = state,
                onEvent = {},
                onNavigateBack = {},
                snackbarHostState = SnackbarHostState(),
            )
        }
    }

    private fun russianListFixtures(): List<MeasurementSummary> = listOf(
        summary(id = 1L, title = "Спальня", note = "вечер", avg = 32f, min = 28f, max = 38f),
        summary(id = 2L, title = "Двор", note = null, avg = 55f, min = 42f, max = 71f),
        summary(id = 3L, title = "Кухня", note = "холодильник", avg = 68f, min = 55f, max = 79f),
        summary(
            id = 4L,
            title = "Метро вечером",
            note = "длинная заметка, описывающая контекст замера",
            avg = 78f,
            min = 65f,
            max = 91f,
        ),
        summary(id = 5L, title = "Стройка", note = null, avg = 95f, min = 78f, max = 103f),
    )

    private fun englishListFixtures(): List<MeasurementSummary> = listOf(
        summary(id = 1L, title = "Bedroom", note = "evening", avg = 32f, min = 28f, max = 38f),
        summary(id = 2L, title = "Backyard", note = null, avg = 55f, min = 42f, max = 71f),
        summary(id = 3L, title = "Kitchen", note = "fridge running", avg = 68f, min = 55f, max = 79f),
        summary(
            id = 4L,
            title = "Subway at night",
            note = "long note describing the context of the measurement",
            avg = 78f,
            min = 65f,
            max = 91f,
        ),
        summary(id = 5L, title = "Construction", note = null, avg = 95f, min = 78f, max = 103f),
    )

    private fun summary(
        id: Long,
        title: String?,
        note: String?,
        avg: Float,
        min: Float,
        max: Float,
    ) = MeasurementSummary(
        id = id,
        createdAtEpochMs = STABLE_CREATED_AT - id * MILLIS_PER_HOUR,
        durationMs = 65_000L + id * 10_000L,
        avgDb = avg,
        minDb = min,
        maxDb = max,
        title = title,
        note = note,
        sparklinePreview = sparkline,
    )

    private fun renderAndCapture(name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                content()
            }
        }
        composeTestRule.onRoot().captureSnapshot("StoreScreenshotTest_$name")
    }

    private companion object {
        const val STABLE_CREATED_AT = 1_727_827_200_000L
        const val MILLIS_PER_HOUR = 3_600_000L
        const val SAMPLE_COUNT = 60
        const val STEP_MS = 1000L
        const val CENTER_DB = 60f
        const val AMPLITUDE_DB = 20f
        const val CYCLES = 3f
    }
}
