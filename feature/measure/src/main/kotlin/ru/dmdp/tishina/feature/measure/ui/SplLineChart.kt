package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.core.designsystem.theme.LocalSplLevelPalette
import ru.dmdp.tishina.core.designsystem.theme.levelToSplColor
import ru.dmdp.tishina.core.domain.model.SoundSample

const val SplLineChartTestTag: String = "measure_spl_line_chart"

/** Y-axis bounds in dB. Matches [SplArcGauge] for visual coherence between the two widgets. */
private const val CHART_MIN_DB = 30f
private const val CHART_MAX_DB = 110f

/** X-axis window in milliseconds — last 60 seconds (spec FR-5). */
private const val CHART_WINDOW_MS = 60_000L

/**
 * Polyline of recent dB samples over the last [CHART_WINDOW_MS] milliseconds.
 *
 * No axes/labels in Phase 2 — keeps the widget visually quiet during active measurement
 * (the readout + gauge already communicate the absolute value). The polyline color is driven
 * by the *latest* sample so the chart conveys "is the level safe right now?" at a glance —
 * not "what was the level when we plotted this point?" which would require per-segment
 * coloring and an extra pass.
 *
 * Empty / single-sample inputs draw nothing rather than a degenerate line.
 */
@Composable
fun SplLineChart(
    samples: List<SoundSample>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 96.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    val palette = LocalSplLevelPalette.current
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = samples.lastOrNull()?.let { levelToSplColor(it.db, palette) }
        ?: palette.veryQuiet

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(contentPadding)
            .testTag(SplLineChartTestTag),
    ) {
        drawTrack(trackColor)
        if (samples.size < 2) return@Canvas
        drawPolyline(samples, lineColor)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrack(color: Color) {
    val strokeWidth = 1.dp.toPx()
    val y = size.height - strokeWidth / 2f
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(0f, y),
        end = androidx.compose.ui.geometry.Offset(size.width, y),
        strokeWidth = strokeWidth,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolyline(
    samples: List<SoundSample>,
    color: Color,
) {
    val latestTs = samples.last().timestampMs
    // Use the actual data window when the buffer holds <60s of samples — otherwise an early
    // chart would render all points squashed into the right edge.
    val earliestTs = samples.first().timestampMs
    val windowMs = maxOf(CHART_WINDOW_MS, latestTs - earliestTs)
    val range = CHART_MAX_DB - CHART_MIN_DB

    val path = Path()
    samples.forEachIndexed { index, sample ->
        val xRatio = (1f - (latestTs - sample.timestampMs).toFloat() / windowMs.toFloat())
            .coerceIn(0f, 1f)
        val x = xRatio * size.width
        val clampedDb = sample.db.coerceIn(CHART_MIN_DB, CHART_MAX_DB)
        val yRatio = (clampedDb - CHART_MIN_DB) / range
        // Y is inverted: high dB → top of canvas.
        val y = size.height - yRatio * size.height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}
