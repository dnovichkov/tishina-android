package ru.dmdp.tishina.feature.history

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
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Baseline for the FR-8 / FR-9 list view rendered via the testable [HistoryScreenContent].
 *
 * Five sample cards across loudness ranges and note variants give one diff that exercises
 * all visual buckets in [ru.dmdp.tishina.core.designsystem.theme.SplLevelPalette] without
 * one-shot per-bucket tests.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class HistoryListScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sparkline = listOf(40f, 45f, 50f, 55f, 60f, 58f, 56f, 52f, 50f, 48f)

    private val sampleItems = listOf(
        summary(id = 1L, title = "Спальня", note = "вечер", avg = 32f, min = 28f, max = 38f),
        summary(id = 2L, title = "Двор", note = null, avg = 55f, min = 42f, max = 71f),
        summary(id = 3L, title = "Кухня", note = "холодильник", avg = 68f, min = 55f, max = 79f),
        summary(
            id = 4L,
            title = "Метро вечером",
            note = "длинная заметка, описывающая контекст замера в подробностях для проверки эллипсиса",
            avg = 78f,
            min = 65f,
            max = 91f,
        ),
        summary(id = 5L, title = "Стройка", note = null, avg = 95f, min = 78f, max = 103f),
    )

    @Test
    fun list_light() = capture(dark = false, name = "list_light")

    @Test
    fun list_dark() = capture(dark = true, name = "list_dark")

    private fun capture(dark: Boolean, name: String) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                HistoryScreenContent(
                    state = HistoryUiState(items = sampleItems, loading = false),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    onNavigateToDetail = {},
                    onNavigateToMeasure = {},
                )
            }
        }
        composeTestRule.onRoot().captureSnapshot("HistoryListScreenshotTest_$name")
    }

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

    private companion object {
        const val STABLE_CREATED_AT = 1_727_827_200_000L
        const val MILLIS_PER_HOUR = 3_600_000L
    }
}
