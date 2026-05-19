package ru.dmdp.tishina.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Pure-Kotlin parameterized tests for the dB SPL -> Color mapping (spec § 6).
 *
 * Boundaries:
 *   <= 40        veryQuiet
 *   41..60       quiet
 *   61..75       moderate
 *   76..85       loud
 *   86..100      veryLoud
 *   > 100        extreme
 *
 * Clamp range: -20f..140f. Inputs outside this range still map to the closest
 * bucket (negative => veryQuiet, ridiculously high => extreme).
 */
class LevelToSplColorTest {

    private val palette = SplLevelPalette(
        veryQuiet = Color(0xFF2E7D32),
        quiet = Color(0xFF7CB342),
        moderate = Color(0xFFFBC02D),
        loud = Color(0xFFF57C00),
        veryLoud = Color(0xFFE64A19),
        extreme = Color(0xFFC62828),
    )

    @DisplayName("maps dB values to the spec-defined SPL palette bucket")
    @ParameterizedTest(name = "{0} dB -> {1}")
    @CsvSource(
        // veryQuiet bucket (<= 40)
        "0,    veryQuiet",
        "30,   veryQuiet",
        "40,   veryQuiet",
        // quiet bucket (41..60)
        "41,   quiet",
        "50,   quiet",
        "60,   quiet",
        // moderate bucket (61..75)
        "61,   moderate",
        "70,   moderate",
        "75,   moderate",
        // loud bucket (76..85)
        "76,   loud",
        "80,   loud",
        "85,   loud",
        // veryLoud bucket (86..100)
        "86,   veryLoud",
        "95,   veryLoud",
        "100,  veryLoud",
        // extreme bucket (> 100)
        "101,  extreme",
        "120,  extreme",
        "130,  extreme",
    )
    fun mapsDbToBucket(db: Float, bucket: String) {
        val expected = palette.bucket(bucket)
        assertEquals(expected, levelToSplColor(db, palette))
    }

    @DisplayName("clamps negative and above-realistic values into the outer buckets")
    @ParameterizedTest(name = "{0} dB -> {1}")
    @CsvSource(
        "-10,  veryQuiet",
        "-20,  veryQuiet",
        "-50,  veryQuiet", // below clamp lower bound, still veryQuiet
        "140,  extreme",
        "150,  extreme", // above clamp upper bound, still extreme
        "200,  extreme",
    )
    fun handlesExtremeInputs(db: Float, bucket: String) {
        val expected = palette.bucket(bucket)
        assertEquals(expected, levelToSplColor(db, palette))
    }

    private fun SplLevelPalette.bucket(name: String): Color = when (name) {
        "veryQuiet" -> veryQuiet
        "quiet" -> quiet
        "moderate" -> moderate
        "loud" -> loud
        "veryLoud" -> veryLoud
        "extreme" -> extreme
        else -> error("Unknown bucket: $name")
    }
}
