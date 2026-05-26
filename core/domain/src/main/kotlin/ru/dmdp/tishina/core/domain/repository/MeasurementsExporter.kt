package ru.dmdp.tishina.core.domain.repository

import ru.dmdp.tishina.core.domain.model.ExportFilter

/**
 * Writes measurement rows as CSV to a caller-supplied destination (FR-20).
 *
 * The destination is described by [targetUriString] — an opaque URI string the
 * implementation knows how to resolve. The use-case layer (and `:core:domain` as a
 * whole) intentionally avoids depending on `android.net.Uri`: it lives outside the
 * pure-Kotlin module boundary. Callers on the Android side pass `uri.toString()`
 * and the `:core:data` implementation parses it back via `Uri.parse(...)`.
 *
 * Contract:
 *  - Returns [Result.success] with the number of measurement rows written (header
 *    excluded). Zero rows is a valid success — the file then contains only a header
 *    line (Excel-friendly).
 *  - Returns [Result.failure] on any IO error opening or writing the stream. The
 *    use-case forwards the failure to the ViewModel which surfaces a snackbar.
 *  - [filter] decides which rows are emitted; an empty `ByIds` set produces a
 *    header-only file (still `Result.success(0)`).
 *  - Implementation closes the OutputStream it opened, even on failure.
 */
interface MeasurementsExporter {

    suspend fun export(targetUriString: String, filter: ExportFilter): Result<Int>
}
