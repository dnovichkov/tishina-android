package ru.dmdp.tishina.core.data.export

import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import java.io.Writer
import java.util.Locale

/**
 * Pure-Kotlin RFC 4180 CSV writer powering FR-20 (export to CSV via SAF).
 *
 * Output format:
 *  - UTF-8 BOM (3 bytes `0xEF 0xBB 0xBF`) at the very start so Excel/LibreOffice on
 *    Windows open the file in UTF-8 instead of falling back to the OS code page
 *    (Cyrillic-heavy `Title`/`Note` columns mojibake under Windows-1251 otherwise).
 *  - CRLF (`\r\n`) line terminator including after the last row (RFC 4180 §2.2).
 *  - Fields wrapped in double quotes if and only if they contain `,` `"` `\n` or `\r`;
 *    inner `"` is doubled (`""`).
 *  - `Locale.US` for numeric formatting (`.` decimal separator) so RU-locale devices
 *    do not emit `42,3` and collide with the comma field separator.
 *  - `null` title/note serialize to empty fields, never to the string `"null"`.
 *
 * The serializer takes a [Writer] so callers can stream directly into the SAF-provided
 * `OutputStream` without materializing the whole file in memory. The companion
 * [MeasurementsExporterImpl] wires it to a `ContentResolver.openOutputStream(uri)`.
 *
 * Single-responsibility: serializer knows nothing about Android, ContentResolver or
 * filters — it just writes whatever list of summaries it's handed.
 */
internal class CsvSerializer {

    fun serialize(measurements: List<MeasurementSummary>, writer: Writer) {
        writer.write(BOM)
        writeRow(writer, HEADER)
        for (m in measurements) {
            writeRow(
                writer,
                listOf(
                    m.id.toString(),
                    m.createdAtEpochMs.toString(),
                    m.durationMs.toString(),
                    formatDb(m.avgDb),
                    formatDb(m.minDb),
                    formatDb(m.maxDb),
                    m.title.orEmpty(),
                    m.note.orEmpty(),
                ),
            )
        }
        writer.flush()
    }

    private fun writeRow(writer: Writer, columns: List<String>) {
        for ((index, value) in columns.withIndex()) {
            if (index > 0) writer.write(",")
            writer.write(escape(value))
        }
        writer.write(LINE_TERMINATOR)
    }

    /**
     * RFC 4180 escaping: wrap in `"` and double inner `"` IFF the value contains a
     * special character. Plain alphanumerics pass through untouched, which keeps the
     * output compact when nothing needs escaping (the common case for numeric columns).
     */
    private fun escape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuoting = value.any { it in SPECIALS }
        if (!needsQuoting) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /** One decimal place, locale-stable. Mirrors the existing UI rendering precision. */
    private fun formatDb(value: Float): String = String.format(Locale.US, "%.1f", value)

    internal companion object {
        /**
         * UTF-8 BOM. The single `U+FEFF` char, when written through an
         * `OutputStreamWriter` configured with `StandardCharsets.UTF_8`, is emitted as
         * the 3-byte sequence `0xEF 0xBB 0xBF` — exactly what Excel for Windows needs
         * to autodetect the file as UTF-8. The literal is the Unicode escape so an
         * editor/Spotless pass that silently strips zero-width characters (a regression
         * that already cost us one CI cycle) cannot delete the byte-order mark, and so
         * Android Lint's `ByteOrderMark` detector does not flag the literal U+FEFF
         * embedded in the middle of this source file.
         */
        private const val BOM: String = "\uFEFF"
        private const val LINE_TERMINATOR: String = "\r\n"
        private val SPECIALS: Set<Char> = setOf(',', '"', '\n', '\r')
        internal val HEADER: List<String> = listOf(
            "id", "createdAt", "durationMs", "avgDb", "minDb", "maxDb", "title", "note",
        )
    }
}
