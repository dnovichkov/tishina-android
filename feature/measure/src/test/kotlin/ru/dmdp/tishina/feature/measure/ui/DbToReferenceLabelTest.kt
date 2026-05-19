package ru.dmdp.tishina.feature.measure.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import ru.dmdp.tishina.feature.measure.R

/**
 * Verifies the dB → reference-label mapping documented in spec § 6.
 *
 * The boundary cases are documented to ensure off-band edges resolve into the lower bucket
 * (e.g. 15.0 → Breathing, 16.0 → Whisper) — the function uses upper-inclusive comparisons
 * just like [ru.dmdp.tishina.core.designsystem.theme.levelToSplColor].
 *
 * Built as a [TestFactory] rather than `@CsvSource` because Android R.string ids are not
 * compile-time constants and cannot appear inside `@CsvSource` annotation values.
 */
class DbToReferenceLabelTest {

    @TestFactory
    fun maps_db_to_reference_label(): List<DynamicTest> {
        val cases = listOf(
            10f to R.string.measure_ref_breath,
            15f to R.string.measure_ref_breath,
            16f to R.string.measure_ref_whisper,
            20f to R.string.measure_ref_whisper,
            30f to R.string.measure_ref_bedroom,
            40f to R.string.measure_ref_library,
            50f to R.string.measure_ref_fridge,
            60f to R.string.measure_ref_conversation,
            70f to R.string.measure_ref_vacuum,
            80f to R.string.measure_ref_traffic,
            90f to R.string.measure_ref_motorbike,
            100f to R.string.measure_ref_subway,
            110f to R.string.measure_ref_concert,
            120f to R.string.measure_ref_thunder,
            140f to R.string.measure_ref_jet,
        )
        return cases.map { (db, expectedRes) ->
            DynamicTest.dynamicTest("$db dB → ref $expectedRes") {
                assertEquals(expectedRes, dbToReferenceLabel(db))
            }
        }
    }

    @Test
    fun nan_input_maps_to_quietest_bucket() {
        assertEquals(R.string.measure_ref_breath, dbToReferenceLabel(Float.NaN))
    }

    @Test
    fun negative_infinity_input_maps_to_quietest_bucket() {
        assertEquals(R.string.measure_ref_breath, dbToReferenceLabel(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun positive_infinity_input_maps_to_quietest_bucket() {
        // !isFinite() short-circuits to breath; do not accidentally fall through to jet.
        assertEquals(R.string.measure_ref_breath, dbToReferenceLabel(Float.POSITIVE_INFINITY))
    }
}
