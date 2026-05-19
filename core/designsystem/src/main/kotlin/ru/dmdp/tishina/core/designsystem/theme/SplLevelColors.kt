package ru.dmdp.tishina.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette for the 6 SPL level buckets defined in spec § 6 (Цветовое кодирование уровней):
 *
 *   <= 40 dB   veryQuiet  — quiet rooms, library, whisper
 *   41..60     quiet      — normal conversation, soft music
 *   61..75     moderate   — busy office, vacuum cleaner
 *   76..85     loud       — heavy traffic, alarm clock
 *   86..100    veryLoud   — motorbike, hair dryer
 *   > 100      extreme    — concert, chainsaw, jet engine
 *
 * Default palette values come straight from the spec and stay stable across
 * light/dark themes — these are reference safety thresholds, not brand colors.
 */
@Immutable
data class SplLevelPalette(
    val veryQuiet: Color,
    val quiet: Color,
    val moderate: Color,
    val loud: Color,
    val veryLoud: Color,
    val extreme: Color,
)

val defaultSplLevelPalette: SplLevelPalette = SplLevelPalette(
    veryQuiet = Color(0xFF2E7D32),
    quiet = Color(0xFF7CB342),
    moderate = Color(0xFFFBC02D),
    loud = Color(0xFFF57C00),
    veryLoud = Color(0xFFE64A19),
    extreme = Color(0xFFC62828),
)

/**
 * CompositionLocal that distributes the active SPL palette through the theme.
 * TishinaTheme installs the default; tests/previews can override per subtree.
 */
val LocalSplLevelPalette = staticCompositionLocalOf { defaultSplLevelPalette }

/**
 * Realistic dB SPL range. -20 dB lies below the human hearing floor (0 dB SPL
 * = 20 µPa), 140 dB is the threshold of immediate pain / hearing damage.
 * Values outside this window are clamped before bucketing — the function is
 * total (never throws) so it is safe to use directly with raw measurements.
 */
private const val MIN_DB = -20f
private const val MAX_DB = 140f

// Bucket boundaries follow spec § 6. Boundaries are upper-inclusive
// (40 -> veryQuiet, 41 -> quiet, 60 -> quiet, 61 -> moderate, ...).
private const val VERY_QUIET_MAX = 40f
private const val QUIET_MAX = 60f
private const val MODERATE_MAX = 75f
private const val LOUD_MAX = 85f
private const val VERY_LOUD_MAX = 100f

/**
 * Maps a dB SPL value to the corresponding [palette] color.
 *
 * Boundaries follow spec § 6 — upper-inclusive buckets:
 * 40 -> veryQuiet, 41 -> quiet, 60 -> quiet, 61 -> moderate, etc.
 */
fun levelToSplColor(db: Float, palette: SplLevelPalette = defaultSplLevelPalette): Color {
    val clamped = db.coerceIn(MIN_DB, MAX_DB)
    return when {
        clamped <= VERY_QUIET_MAX -> palette.veryQuiet
        clamped <= QUIET_MAX -> palette.quiet
        clamped <= MODERATE_MAX -> palette.moderate
        clamped <= LOUD_MAX -> palette.loud
        clamped <= VERY_LOUD_MAX -> palette.veryLoud
        else -> palette.extreme
    }
}
