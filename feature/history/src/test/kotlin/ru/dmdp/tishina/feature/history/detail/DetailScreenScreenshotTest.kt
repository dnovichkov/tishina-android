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
import kotlin.math.PI
import kotlin.math.sin

/**
 * Three baseline scenarios × light/dark = 6 PNGs for [DetailScreen]:
 *  - readonly with a populated note (visible plain Text)
 *  - edit-mode with an OutlinedTextField and counter
 *  - empty-note → placeholder "Добавить заметку"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class DetailScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val syntheticSamples = (0 until SAMPLE_COUNT).map { i ->
        val t = i.toFloat() / SAMPLE_COUNT.toFloat()
        val db = CENTER_DB + AMPLITUDE_DB * sin(t * 2f * PI.toFloat() * CYCLES)
        SoundSample(db = db, timestampMs = (i * STEP_MS).toLong())
    }

    @Test
    fun readonly_light() = capture(scenario = Scenario.Readonly, dark = false, name = "readonly_light")

    @Test
    fun readonly_dark() = capture(scenario = Scenario.Readonly, dark = true, name = "readonly_dark")

    @Test
    fun edit_light() = capture(scenario = Scenario.Edit, dark = false, name = "edit_light")

    @Test
    fun edit_dark() = capture(scenario = Scenario.Edit, dark = true, name = "edit_dark")

    @Test
    fun empty_note_light() = capture(scenario = Scenario.EmptyNote, dark = false, name = "empty_note_light")

    @Test
    fun empty_note_dark() = capture(scenario = Scenario.EmptyNote, dark = true, name = "empty_note_dark")

    private enum class Scenario { Readonly, Edit, EmptyNote }

    private fun capture(scenario: Scenario, dark: Boolean, name: String) {
        val note = when (scenario) {
            Scenario.Readonly -> "Соседи слушают музыку через стену, замер сделан в 22:30."
            Scenario.Edit -> "Соседи слушают музыку через стену, замер сделан в 22:30."
            Scenario.EmptyNote -> null
        }
        val state = DetailUiState(
            details = sampleDetails(note = note),
            noteDraft = note.orEmpty(),
            editingNote = scenario == Scenario.Edit,
            loading = false,
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
        composeTestRule.onRoot().captureSnapshot("DetailScreenScreenshotTest_$name")
    }

    private fun sampleDetails(note: String?): MeasurementDetails {
        val summary = MeasurementSummary(
            id = 1L,
            createdAtEpochMs = STABLE_CREATED_AT,
            durationMs = 75_000L,
            avgDb = 60f,
            minDb = 42f,
            maxDb = 78f,
            title = "Спальня",
            note = note,
            sparklinePreview = emptyList(),
        )
        return MeasurementDetails(
            summary = summary,
            samples = syntheticSamples,
            weighting = FrequencyWeighting.A,
            timeWeighting = TimeWeighting.FAST,
            calibrationOffsetDb = 0f,
            sampleRateHz = 48_000,
        )
    }

    private companion object {
        const val STABLE_CREATED_AT = 1_727_827_200_000L
        const val SAMPLE_COUNT = 60
        const val STEP_MS = 1000L
        const val CENTER_DB = 60f
        const val AMPLITUDE_DB = 20f
        const val CYCLES = 3f
    }
}
