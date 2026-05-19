package ru.dmdp.tishina.feature.measure.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.core.designsystem.theme.LocalSplLevelPalette
import ru.dmdp.tishina.core.designsystem.theme.levelToSplColor
import ru.dmdp.tishina.feature.measure.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val SplArcGaugeTestTag: String = "measure_spl_arc_gauge"
const val SplArcGaugeReferenceTestTag: String = "measure_spl_arc_gauge_reference"

/** Lowest dB shown on the arc. */
private const val GAUGE_MIN_DB = 30f

/** Highest dB shown on the arc. */
private const val GAUGE_MAX_DB = 110f

/** Sweep angle in degrees — 270° opens at the bottom for a "fuel gauge" look. */
private const val SWEEP_DEGREES = 270f

/** Start angle — Canvas 0° points right; we rotate so the arc opens at the bottom. */
private const val START_ANGLE_DEGREES = 135f

/**
 * Dial gauge for the current dB value with a textual reference cue below.
 *
 * The arc renders as a 270° sweep with a six-color gradient matching the SPL palette so the
 * color encoding stays consistent with [SplReadout]. The needle indicator interpolates the
 * current dB onto the arc, clamped to the [GAUGE_MIN_DB] … [GAUGE_MAX_DB] window — values
 * outside that range are rare in everyday measurement and the dial would otherwise misrepresent
 * scale.
 *
 * The reference label is the textual hook ("Whisper", "Concert", …) that lets non-experts
 * interpret dB on first glance. See spec § 6 reference table.
 */
@Composable
fun SplArcGauge(
    db: Float,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val palette = LocalSplLevelPalette.current
    val needleColor = levelToSplColor(db, palette)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val referenceText = stringResource(id = dbToReferenceLabel(db))
    val accessibilityLabel = stringResource(id = R.string.measure_gauge_cd, db)
    val gradientColors = listOf(
        palette.veryQuiet,
        palette.quiet,
        palette.moderate,
        palette.loud,
        palette.veryLoud,
        palette.extreme,
    )

    Column(
        modifier = modifier
            .padding(contentPadding)
            .testTag(SplArcGaugeTestTag)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f),
        ) {
            drawArcTrack(trackColor)
            drawArcGradient(gradientColors)
            drawNeedle(db = db, color = needleColor)
        }

        Text(
            text = referenceText,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalContentColor.current,
            modifier = Modifier.testTag(SplArcGaugeReferenceTestTag),
        )
    }
}

/**
 * Reference-label buckets from spec § 6. Each entry's `Float` is the upper-inclusive dB
 * boundary; the first entry whose boundary ≥ db wins. Sorted ascending so the linear scan
 * below short-circuits at the right level. Values above the last boundary fall through to
 * [R.string.measure_ref_jet].
 */
private val ReferenceBuckets: List<Pair<Float, Int>> = listOf(
    15f to R.string.measure_ref_breath,
    25f to R.string.measure_ref_whisper,
    35f to R.string.measure_ref_bedroom,
    45f to R.string.measure_ref_library,
    55f to R.string.measure_ref_fridge,
    65f to R.string.measure_ref_conversation,
    75f to R.string.measure_ref_vacuum,
    85f to R.string.measure_ref_traffic,
    95f to R.string.measure_ref_motorbike,
    105f to R.string.measure_ref_subway,
    115f to R.string.measure_ref_concert,
    125f to R.string.measure_ref_thunder,
)

private const val REFERENCE_CLAMP_MIN = 0f
private const val REFERENCE_CLAMP_MAX = 200f

/**
 * Maps a dB value to a string-resource id describing a familiar reference sound.
 *
 * Boundaries follow spec § 6 — upper-inclusive buckets in 10 dB steps.
 * Non-finite inputs fall back to the quietest reference rather than the loudest, matching
 * [levelToSplColor]'s behavior so a broken upstream signal does not flash a "jet engine"
 * caption.
 */
@StringRes
fun dbToReferenceLabel(db: Float): Int {
    if (!db.isFinite()) return R.string.measure_ref_breath
    val clamped = db.coerceIn(REFERENCE_CLAMP_MIN, REFERENCE_CLAMP_MAX)
    return ReferenceBuckets.firstOrNull { (boundary, _) -> clamped <= boundary }?.second
        ?: R.string.measure_ref_jet
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcTrack(color: Color) {
    drawArc(
        color = color,
        startAngle = START_ANGLE_DEGREES,
        sweepAngle = SWEEP_DEGREES,
        useCenter = false,
        topLeft = arcTopLeft(),
        size = arcSize(),
        style = Stroke(width = trackStrokeWidth(), cap = StrokeCap.Round),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcGradient(colors: List<Color>) {
    drawArc(
        brush = Brush.sweepGradient(colors = colors, center = center),
        startAngle = START_ANGLE_DEGREES,
        sweepAngle = SWEEP_DEGREES,
        useCenter = false,
        topLeft = arcTopLeft(),
        size = arcSize(),
        style = Stroke(width = gradientStrokeWidth(), cap = StrokeCap.Round),
        alpha = 0.85f,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNeedle(db: Float, color: Color) {
    val ratio = (
        (db.coerceIn(GAUGE_MIN_DB, GAUGE_MAX_DB) - GAUGE_MIN_DB) /
            (GAUGE_MAX_DB - GAUGE_MIN_DB)
        ).coerceIn(0f, 1f)
    val angleDeg = START_ANGLE_DEGREES + SWEEP_DEGREES * ratio
    val angleRad = Math.toRadians(angleDeg.toDouble())
    val radius = arcSize().minDimension / 2f
    val end = Offset(
        x = center.x + (radius * cos(angleRad)).toFloat(),
        y = center.y + (radius * sin(angleRad)).toFloat(),
    )
    drawLine(
        color = color,
        start = center,
        end = end,
        strokeWidth = needleStrokeWidth(),
        cap = StrokeCap.Round,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.arcSize(): Size {
    val diameter = min(size.width, size.height) - trackStrokeWidth()
    return Size(diameter, diameter)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.arcTopLeft(): Offset =
    Offset(
        x = (size.width - arcSize().width) / 2f,
        y = (size.height - arcSize().height) / 2f,
    )

private fun androidx.compose.ui.graphics.drawscope.DrawScope.trackStrokeWidth(): Float = 24.dp.toPx()

private fun androidx.compose.ui.graphics.drawscope.DrawScope.gradientStrokeWidth(): Float = 16.dp.toPx()

private fun androidx.compose.ui.graphics.drawscope.DrawScope.needleStrokeWidth(): Float = 6.dp.toPx()
