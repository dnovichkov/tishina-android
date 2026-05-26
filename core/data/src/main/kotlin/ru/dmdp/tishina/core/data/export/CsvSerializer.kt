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
 *  - OWASP CSV-injection mitigation: a leading `=`, `+`, `-`, `@`, TAB or CR is prefixed
 *    with a literal `'` so Excel/LibreOffice treats the cell as text rather than
 *    evaluating it as a formula (defeats `=HYPERLINK(...)`, `=cmd|...`, etc).
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
     * RFC 4180 escaping plus OWASP CSV-injection mitigation (Excel/LibreOffice formula
     * execution via leading `=`/`+`/`-`/`@`/TAB/CR). The defence is to prefix any value
     * whose first character matches [FORMULA_TRIGGERS] with a single quote `'`, which
     * Excel renders as a literal text leader and refuses to evaluate as a formula. The
     * single-quote prefix is preserved when the resulting field is quoted, so round-trip
     * parsers see the prefix as user content and the formula engine never gets a chance
     * to evaluate it.
     *
     * The user-controlled `title` and `note` columns are the realistic attack surface —
     * a malicious cell like `=HYPERLINK("http://evil","Click")` would otherwise launch
     * when a victim opens the exported CSV in Excel. We sanitize uniformly across all
     * columns so a future schema change (e.g. negative `calibrationOffsetDb` formatted
     * as `-3.0`) doesn't reintroduce the attack vector without anyone noticing.
     */
    private fun escape(value: String): String {
        if (value.isEmpty()) return ""
        val sanitized = if (value.first() in FORMULA_TRIGGERS) "'$value" else value
        val needsQuoting = sanitized.any { it in SPECIALS }
        if (!needsQuoting) return sanitized
        val escaped = sanitized.replace("\"", "\"\"")
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

        // OWASP-recommended set of "formula trigger" characters. A cell starting with any
        // of these in Excel/LibreOffice is parsed as a formula or as a tab-separated
        // injection helper; prefixing the value with a literal `'` neutralises the
        // formula engine while keeping the cell readable as text.
        private val FORMULA_TRIGGERS: Set<Char> = setOf('=', '+', '-', '@', '\t', '\r')
        internal val HEADER: List<String> = listOf(
            "id", "createdAt", "durationMs", "avgDb", "minDb", "maxDb", "title", "note",
        )
    }
}
