package ru.dmdp.tishina.feature.history.detail.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.feature.history.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 6 Task 4 — builds the `Intent.ACTION_SEND` payload for the Detail share action
 * (FR-10 P1).
 *
 * Two paths:
 *  - **Rich** — when [build] receives a non-null `chartBitmap`, the PNG is written under
 *    `cacheDir/<cacheSubdir>/tishina-<id>-<timestamp>.png` and the resulting URI is
 *    attached via `EXTRA_STREAM`. `mimeType = "image/png"`, `FLAG_GRANT_READ_URI_PERMISSION`.
 *  - **Fallback** — when `chartBitmap` is null OR the cache write fails, the Intent is
 *    text-only (`mimeType = "text/plain"`, no stream). The receiving app still gets the
 *    textual summary, which is the most important payload for messengers like SMS.
 *
 * The URI provider is injected as a `(File) -> Uri` so unit tests can replace
 * `FileProvider.getUriForFile` (which requires a `<provider>` manifest declaration that
 * library-module unit tests don't have). Production wires it via the `@Provides` factory
 * in `HistoryUseCaseModule` using the `${applicationId}.fileprovider` authority.
 */
class ShareIntentBuilder(private val context: Context, private val cacheSubdir: String = "share", private val fileToUri: (File) -> Uri) {

    fun build(details: MeasurementDetails, chartBitmap: Bitmap?): Intent {
        val text = buildSummaryText(details)
        if (chartBitmap == null) {
            return textOnlyIntent(text)
        }
        val pngUri = writePngToCache(details.summary.id, chartBitmap)
        return if (pngUri != null) {
            Intent(Intent.ACTION_SEND).apply {
                type = MIME_PNG
                putExtra(Intent.EXTRA_STREAM, pngUri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            // Cache IO failed — degrade to text-only rather than propagate up to the UI.
            // Users still get a useful payload; the receiving app gets a valid Intent.
            textOnlyIntent(text)
        }
    }

    private fun textOnlyIntent(text: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = MIME_TEXT
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun writePngToCache(measurementId: Long, bitmap: Bitmap): Uri? {
        return try {
            val shareDir = File(context.cacheDir, cacheSubdir)
            // mkdirs() returns false both when the directory already exists AND when a regular
            // file occupies that path. We branch on the post-call state, not the boolean.
            if (!shareDir.isDirectory) {
                shareDir.mkdirs()
                if (!shareDir.isDirectory) return null
            }
            val filename = "tishina-$measurementId-${System.currentTimeMillis()}.png"
            val file = File(shareDir, filename)
            file.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
            }
            fileToUri(file)
        } catch (_: Throwable) {
            null
        }
    }

    private fun buildSummaryText(details: MeasurementDetails): String {
        val summary = details.summary
        val title = summary.title?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.detail_note_default_title)
        val recorded = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())
            .format(Date(summary.createdAtEpochMs))
        val avgLabel = context.getString(R.string.detail_share_avg, summary.avgDb)
        val minLabel = context.getString(R.string.detail_share_min, summary.minDb)
        val maxLabel = context.getString(R.string.detail_share_max, summary.maxDb)
        val durationLabel = context.getString(
            R.string.detail_share_duration,
            formatDuration(summary.durationMs),
        )
        val footer = context.getString(R.string.detail_share_footer)
        return buildString {
            append(title)
            append('\n')
            append(recorded)
            append("\n\n")
            append(avgLabel)
            append('\n')
            append(minLabel)
            append('\n')
            append(maxLabel)
            append('\n')
            append(durationLabel)
            append("\n\n—\n")
            append(footer)
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / MS_PER_SECOND).coerceAtLeast(0L)
        val minutes = seconds / SECONDS_PER_MINUTE
        val remainder = seconds % SECONDS_PER_MINUTE
        return "%02d:%02d".format(minutes, remainder)
    }

    private companion object {
        const val MIME_PNG = "image/png"
        const val MIME_TEXT = "text/plain"
        const val PNG_QUALITY = 100
        const val DATE_PATTERN = "d MMM yyyy, HH:mm"
        const val MS_PER_SECOND = 1000L
        const val SECONDS_PER_MINUTE = 60L
    }
}
