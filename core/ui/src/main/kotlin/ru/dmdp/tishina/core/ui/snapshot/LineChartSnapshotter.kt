package ru.dmdp.tishina.core.ui.snapshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import kotlinx.coroutines.CancellationException
import ru.dmdp.tishina.core.domain.model.SoundSample
import kotlin.math.roundToInt

/**
 * Phase 6 Task 4 — off-screen renderer that mirrors `SplLineChart`'s visual contract on a
 * standalone [Bitmap]. Used by the Detail share Intent to attach a PNG snapshot of the chart
 * without needing a live `ComposeView`.
 *
 * The bitmap is rendered with pure `android.graphics.Canvas` API so the call is dispatcher-
 * safe (no Compose layout cycle, no main thread requirement). Colors are hard-coded to the
 * neutral palette used by the live chart so the snapshot reads as the same widget — but we
 * deliberately don't depend on `MaterialTheme` here: the share image must look the same
 * whether the user has light or dark theme active, because the receiving app (e.g. Telegram,
 * WhatsApp) renders the PNG on its own surface.
 *
 * Failure modes returned via [Result.failure]:
 *  - non-positive width/height — `Bitmap.createBitmap` would throw IllegalArgumentException;
 *    callers fall back to text-only share.
 *  - any other exception during draw — caller's responsibility to fall back; we never let
 *    `CancellationException` escape the wrapper.
 */
open class LineChartSnapshotter {

    open suspend fun snapshot(
        samples: List<SoundSample>,
        widthPx: Int,
        heightPx: Int,
    ): Result<Bitmap> {
        if (widthPx <= 0 || heightPx <= 0) {
            return Result.failure(IllegalArgumentException("width and height must be positive"))
        }
        return try {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawBackground(canvas, widthPx, heightPx)
            drawTrack(canvas, widthPx, heightPx)
            if (samples.size >= MIN_SAMPLES_FOR_POLYLINE) {
                drawPolyline(canvas, samples, widthPx, heightPx)
            }
            Result.success(bitmap)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int) {
        // Solid neutral grey makes the chart legible against arbitrary destinations
        // (chat backgrounds, email previews) — transparent PNG would render invisibly
        // on dark themes for receivers that don't paint a backdrop.
        val paint = Paint().apply { color = BACKGROUND_COLOR }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawTrack(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint().apply {
            color = TRACK_COLOR
            strokeWidth = TRACK_STROKE_PX
            isAntiAlias = true
        }
        val y = height - TRACK_STROKE_PX / 2f
        canvas.drawLine(0f, y, width.toFloat(), y, paint)
    }

    private fun drawPolyline(
        canvas: Canvas,
        samples: List<SoundSample>,
        width: Int,
        height: Int,
    ) {
        val latestTs = samples.last().timestampMs
        val earliestTs = samples.first().timestampMs
        val windowMs = maxOf(CHART_WINDOW_MS, latestTs - earliestTs)
        val range = CHART_MAX_DB - CHART_MIN_DB

        val path = Path()
        samples.forEachIndexed { index, sample ->
            val xRatio = (1f - (latestTs - sample.timestampMs).toFloat() / windowMs.toFloat())
                .coerceIn(0f, 1f)
            val x = xRatio * width
            val clampedDb = sample.db.coerceIn(CHART_MIN_DB, CHART_MAX_DB)
            val yRatio = (clampedDb - CHART_MIN_DB) / range
            val y = height - yRatio * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        val paint = Paint().apply {
            color = colorForLastSample(samples.last().db)
            strokeWidth = LINE_STROKE_DP * DENSITY_PX_PER_DP
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        canvas.drawPath(path, paint)
    }

    private fun colorForLastSample(db: Float): Int {
        // Coarse mapping mirroring SplLevelPalette buckets used by the live chart. We don't
        // reach into MaterialTheme because the snapshot must be theme-independent (see kdoc).
        val rounded = db.roundToInt()
        return when {
            rounded < QUIET_DB -> COLOR_QUIET
            rounded < MODERATE_DB -> COLOR_MODERATE
            rounded < LOUD_DB -> COLOR_LOUD
            else -> COLOR_DANGER
        }
    }

    private companion object {
        // Visual constants chosen to mirror the live `SplLineChart` widget without taking
        // a runtime dependency on Compose theme tokens.
        const val MIN_SAMPLES_FOR_POLYLINE = 2
        const val CHART_WINDOW_MS = 60_000L
        const val CHART_MIN_DB = 30f
        const val CHART_MAX_DB = 110f

        const val LINE_STROKE_DP = 3f
        const val TRACK_STROKE_PX = 2f
        const val DENSITY_PX_PER_DP = 2f

        // 0xFFEFEFEF — light neutral grey close to Material `surfaceVariant` in light mode.
        const val BACKGROUND_COLOR = 0xFFEFEFEF.toInt()
        const val TRACK_COLOR = 0xFFB0B0B0.toInt()

        const val QUIET_DB = 50
        const val MODERATE_DB = 70
        const val LOUD_DB = 85

        val COLOR_QUIET: Int = Color.parseColor("#0FB5BA")
        val COLOR_MODERATE: Int = Color.parseColor("#5C9DAB")
        val COLOR_LOUD: Int = Color.parseColor("#E0A800")
        val COLOR_DANGER: Int = Color.parseColor("#D8534F")
    }
}
