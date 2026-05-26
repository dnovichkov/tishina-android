package ru.dmdp.tishina.core.ui.snapshot

import android.graphics.BitmapFactory
import android.graphics.Color
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.domain.model.SoundSample
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.sin

/**
 * Phase 6 Task 4 — TDD contract for [LineChartSnapshotter].
 *
 * Why off-screen rendering rather than reusing the live `SplLineChart` composable: the share
 * Intent must work even when the user has navigated away from the chart (e.g. from Detail's
 * TopAppBar action while the parent is in transition). Rendering through Compose layout
 * would require a live `ComposeView` and a paused-frame snapshot — slow, fragile, and not
 * deterministic under Robolectric. Drawing through pure `android.graphics.Canvas` API gives
 * us identical visual output (the chart's draw functions are reused) with no Compose runtime
 * dependency, so the snapshotter can be invoked from a background dispatcher and tested as
 * a plain unit.
 *
 * Robolectric NATIVE graphics mode is required because `Bitmap.createBitmap`/`Canvas.drawPath`
 * fall back to a no-op stub under LEGACY mode — pixels would all be zero and the coverage
 * assertion would falsely pass even if the polyline was dropped from `LineChartSnapshotter`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class LineChartSnapshotterTest {

    private val snapshotter = LineChartSnapshotter()

    @Test
    fun `produces bitmap of requested size for non-empty samples`() = runTest {
        val samples = sineWave(count = 60)

        val result = snapshotter.snapshot(
            samples = samples,
            widthPx = TARGET_WIDTH,
            heightPx = TARGET_HEIGHT,
        )

        val bitmap = result.getOrNull()
        assertNotNull("expected success result for valid samples", bitmap)
        requireNotNull(bitmap)
        assertEquals(TARGET_WIDTH, bitmap.width)
        assertEquals(TARGET_HEIGHT, bitmap.height)
    }

    @Test
    fun `bitmap is encodable as PNG and re-decodable`() = runTest {
        val samples = sineWave(count = 30)

        val bitmap = snapshotter.snapshot(samples, TARGET_WIDTH, TARGET_HEIGHT).getOrThrow()
        val outputStream = ByteArrayOutputStream()
        val compressed = bitmap.compress(
            android.graphics.Bitmap.CompressFormat.PNG,
            PNG_QUALITY,
            outputStream,
        )

        assertTrue("PNG.compress must succeed for a valid bitmap", compressed)
        val bytes = outputStream.toByteArray()
        assertTrue("PNG payload must be non-trivial", bytes.size > 100)
        val roundTrip = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull("PNG round-trip should be decodable", roundTrip)
        assertEquals(TARGET_WIDTH, roundTrip.width)
        assertEquals(TARGET_HEIGHT, roundTrip.height)
    }

    @Test
    fun `polyline pixels are visible on rendered bitmap`() = runTest {
        // 60 samples sweeping 40-80 dB through three full cycles will paint a curve across
        // most of the canvas. We only assert "at least one pixel is non-background" — exact
        // coverage depends on stroke width and anti-aliasing, which we don't pin here.
        val samples = sineWave(count = 60)

        val bitmap = snapshotter.snapshot(samples, TARGET_WIDTH, TARGET_HEIGHT).getOrThrow()
        val nonBackground = countNonTransparentPixels(bitmap)

        assertTrue(
            "expected polyline pixels, got $nonBackground / ${bitmap.width * bitmap.height}",
            nonBackground > 0,
        )
    }

    @Test
    fun `empty samples list renders an empty bitmap without crashing`() = runTest {
        val result = snapshotter.snapshot(
            samples = emptyList(),
            widthPx = TARGET_WIDTH,
            heightPx = TARGET_HEIGHT,
        )

        // Per the live `SplLineChart`: < 2 samples → draw the baseline track only, no
        // polyline. The bitmap must still come back at requested size so the share Intent
        // can attach it (text-only fallback is only for catastrophic Canvas failures).
        val bitmap = result.getOrNull()
        assertNotNull("empty samples should still yield a bitmap", bitmap)
        requireNotNull(bitmap)
        assertEquals(TARGET_WIDTH, bitmap.width)
        assertEquals(TARGET_HEIGHT, bitmap.height)
    }

    @Test
    fun `single sample list renders without crash`() = runTest {
        // `SplLineChart` draws nothing for size < 2; ensure the snapshotter mirrors that
        // contract without attempting a degenerate single-point path that some Skia builds
        // refuse to stroke.
        val result = snapshotter.snapshot(
            samples = listOf(SoundSample(db = 50f, timestampMs = 0L)),
            widthPx = TARGET_WIDTH,
            heightPx = TARGET_HEIGHT,
        )

        assertNotNull(result.getOrNull())
    }

    @Test
    fun `non-positive dimensions yield failure result`() = runTest {
        // Defense-in-depth — `Bitmap.createBitmap(0, h, ...)` throws IllegalArgumentException
        // unconditionally; we map it to a Result.failure so the caller can fall back to
        // text-only share rather than crash the activity.
        val zeroWidth = snapshotter.snapshot(sineWave(10), widthPx = 0, heightPx = 200)
        val zeroHeight = snapshotter.snapshot(sineWave(10), widthPx = 200, heightPx = 0)
        val negative = snapshotter.snapshot(sineWave(10), widthPx = -10, heightPx = 200)

        assertTrue("widthPx=0 should fail", zeroWidth.isFailure)
        assertTrue("heightPx=0 should fail", zeroHeight.isFailure)
        assertTrue("negative width should fail", negative.isFailure)
    }

    private fun sineWave(count: Int): List<SoundSample> =
        (0 until count).map { i ->
            val t = i.toFloat() / count.toFloat()
            val db = CENTER_DB + AMPLITUDE_DB * sin(t * 2f * PI.toFloat() * CYCLES)
            SoundSample(db = db, timestampMs = (i * STEP_MS).toLong())
        }

    private fun countNonTransparentPixels(bitmap: android.graphics.Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count { Color.alpha(it) > 0 }
    }

    private companion object {
        const val TARGET_WIDTH = 480
        const val TARGET_HEIGHT = 240
        const val STEP_MS = 1000L
        const val CENTER_DB = 60f
        const val AMPLITUDE_DB = 20f
        const val CYCLES = 3f
        const val PNG_QUALITY = 100
    }
}
