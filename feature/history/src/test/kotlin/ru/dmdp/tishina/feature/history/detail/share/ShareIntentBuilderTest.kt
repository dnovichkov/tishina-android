package ru.dmdp.tishina.feature.history.detail.share

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Phase 6 Task 4 — TDD contract for [ShareIntentBuilder].
 *
 * The Detail screen's Share action must work in two modes:
 *  1. **Rich** — Bitmap snapshot of the chart available → Intent.ACTION_SEND with
 *     `mimeType=image/png` + EXTRA_STREAM (FileProvider URI) + EXTRA_TEXT summary.
 *  2. **Fallback** — snapshot rendering failed → Intent.ACTION_SEND with
 *     `mimeType=text/plain` + EXTRA_TEXT only.
 *
 * The fallback path keeps Share from looking broken on devices where the Canvas backend
 * rejects the off-screen draw — the user still gets the textual summary in their messenger.
 *
 * Design note — *injectable URI provider*: the production wiring uses
 * `androidx.core.content.FileProvider.getUriForFile(...)` which requires a `<provider>`
 * declaration in the consuming app's manifest (`:app` here). In a library-module unit test
 * we don't have that manifest, so the builder takes a `fileToUri: (File) -> Uri` function
 * as its second constructor argument. Tests substitute a synthetic `content://test/...`
 * URI; production wires through Hilt to the FileProvider call site. Testing the FileProvider
 * + manifest wiring itself is the job of the Phase 6 Task 9 instrumentation suite.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class ShareIntentBuilderTest {

    private lateinit var context: Application
    private lateinit var builder: ShareIntentBuilder
    private val uriCallLog = mutableListOf<java.io.File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        uriCallLog.clear()
        builder = ShareIntentBuilder(
            context = context,
            cacheSubdir = "share",
            fileToUri = { file ->
                uriCallLog.add(file)
                Uri.parse("content://test.fileprovider/${file.name}")
            },
        )
    }

    @Test
    fun `text-only fallback when bitmap is null`() {
        val intent = builder.build(details = sampleDetails(title = "Office"), chartBitmap = null)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertNull(
            "text-only path must not attach a stream",
            androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java),
        )
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        assertNotNull("EXTRA_TEXT must always be present", text)
        requireNotNull(text)
        assertTrue("text must contain the title", text.contains("Office"))
        assertTrue("text must contain avg dB", text.contains("60"))
        assertTrue("text-only path must not write to cache", uriCallLog.isEmpty())
    }

    @Test
    fun `rich path attaches PNG stream and keeps text summary`() {
        val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }

        val intent = builder.build(details = sampleDetails(title = "Office"), chartBitmap = bitmap)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        val streamUri = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        assertNotNull("rich path must attach the PNG via EXTRA_STREAM", streamUri)
        assertEquals("content", streamUri?.scheme)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        assertNotNull(text)
        assertTrue(text!!.contains("Office"))
        // The builder asked the URI provider for exactly one file → guards against accidentally
        // writing multiple PNGs per share (which would leak cache space).
        assertEquals(1, uriCallLog.size)
    }

    @Test
    fun `intent grants temporary read URI permission to receiver`() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        val intent = builder.build(details = sampleDetails(), chartBitmap = bitmap)

        assertTrue(
            "FLAG_GRANT_READ_URI_PERMISSION is required so the receiving app can open the URI",
            intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
    }

    @Test
    fun `cache file is written as decodable PNG`() {
        val original = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
        }

        builder.build(details = sampleDetails(), chartBitmap = original)

        val writtenFile = uriCallLog.single()
        assertTrue("PNG file must exist on disk", writtenFile.isFile)
        val decoded = BitmapFactory.decodeFile(writtenFile.absolutePath)
        assertNotNull("written PNG must be decodable", decoded)
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
    }

    @Test
    fun `cache file lives under the configured subdirectory`() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)

        builder.build(details = sampleDetails(), chartBitmap = bitmap)

        val writtenFile = uriCallLog.single()
        // We don't pin the exact filename (timestamp-based), only the parent — guards
        // against accidentally writing to /cache/ root or to /files/ (FileProvider wouldn't
        // be able to expose the latter under `<cache-path>`).
        assertEquals(
            context.cacheDir.resolve("share").absolutePath,
            writtenFile.parentFile?.absolutePath,
        )
        assertTrue("must be a PNG by extension", writtenFile.name.endsWith(".png"))
    }

    @Test
    fun `default title fallback for null title`() {
        val intent = builder.build(details = sampleDetails(title = null), chartBitmap = null)

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        assertNotNull(text)
        // Don't pin the exact resource string (locale-dependent); assert the dB metric is
        // present so the payload remains useful even when the user didn't enter a title.
        assertTrue("text must still contain a metric", text!!.contains("60"))
    }

    @Test
    fun `text summary contains all three metrics`() {
        val intent = builder.build(
            details = sampleDetails(title = "Bedroom"),
            chartBitmap = null,
        )

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue("avg dB present", text.contains("60"))
        assertTrue("min dB present", text.contains("55"))
        assertTrue("max dB present", text.contains("65"))
    }

    @Test
    fun `cache write failure falls back to text-only without throwing`() {
        // Pre-create a regular file at the place where the subdir would live. mkdirs() will
        // refuse to overwrite a file with a directory, so the rich path must degrade to
        // text-only rather than propagate the IO failure to the UI layer.
        val collidingFile = context.cacheDir.resolve("share")
        collidingFile.parentFile?.mkdirs()
        collidingFile.writeText("not a directory")

        try {
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            val intent = builder.build(sampleDetails(), bitmap)

            assertEquals("must degrade to text-only on IO failure", "text/plain", intent.type)
            assertNull(androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            assertFalse(
                "URI provider must not be invoked when the cache write fails",
                uriCallLog.any(),
            )
        } finally {
            collidingFile.delete()
        }
    }

    private fun sampleDetails(
        title: String? = "test",
        avg: Float = 60f,
        min: Float = 55f,
        max: Float = 65f,
    ): MeasurementDetails {
        val summary = MeasurementSummary(
            id = 1L,
            createdAtEpochMs = 1_700_000_000_000L,
            durationMs = 60_000L,
            avgDb = avg,
            minDb = min,
            maxDb = max,
            title = title,
            note = null,
            sparklinePreview = emptyList(),
        )
        return MeasurementDetails(
            summary = summary,
            samples = listOf(SoundSample(60f, 0L)),
            weighting = FrequencyWeighting.A,
            timeWeighting = TimeWeighting.FAST,
            calibrationOffsetDb = 0f,
            sampleRateHz = 48_000,
        )
    }
}
