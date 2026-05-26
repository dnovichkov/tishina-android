package ru.dmdp.tishina.core.data.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Contract for [CsvSerializer] — the pure CSV writer powering FR-20.
 *
 * RFC 4180 + Excel-compat tweaks:
 *  - UTF-8 BOM at the start so Excel/LibreOffice opens the file in UTF-8 instead of
 *    falling back to the OS code page (Windows-1251 on RU Excel would mojibake titles).
 *  - CRLF line endings between rows; LF-only files break Excel for Windows.
 *  - Field-level escaping: a value containing `,` `"` `\n` or `\r` is wrapped in double
 *    quotes; inner `"` is doubled (`""`).
 *  - Numeric formatting uses `Locale.US` so the decimal separator stays `.` regardless
 *    of the user's locale — otherwise an RU device would emit `42,3` which collides
 *    with the field separator.
 *  - `null` title/note serialize to an empty field (no quoting needed).
 *
 * Lives in `:core:data` because it has no Android dependencies but is paired with
 * [MeasurementsExporter] which writes to a `ContentResolver` URI. Plain JUnit 5 — no
 * Robolectric needed.
 */
@DisplayName("CsvSerializer — RFC 4180 + Excel-compat output for FR-20")
class CsvSerializerTest {

    private fun serialize(measurements: List<MeasurementSummary>): String {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, StandardCharsets.UTF_8).use { writer ->
            CsvSerializer().serialize(measurements, writer)
        }
        // Strip BOM for the text-level assertions; BOM is checked separately as bytes.
        val bytes = baos.toByteArray()
        val bomLen = if (bytes.size >= BOM_BYTES.size &&
            bytes[0] == BOM_BYTES[0] &&
            bytes[1] == BOM_BYTES[1] &&
            bytes[2] == BOM_BYTES[2]
        ) {
            BOM_BYTES.size
        } else {
            0
        }
        return String(bytes, bomLen, bytes.size - bomLen, StandardCharsets.UTF_8)
    }

    private fun serializeBytes(measurements: List<MeasurementSummary>): ByteArray {
        val baos = ByteArrayOutputStream()
        OutputStreamWriter(baos, StandardCharsets.UTF_8).use { writer ->
            CsvSerializer().serialize(measurements, writer)
        }
        return baos.toByteArray()
    }

    private fun summary(
        id: Long = 1L,
        createdAt: Long = 1_716_000_000_000L,
        durationMs: Long = 10_000L,
        avgDb: Float = 42.567f,
        minDb: Float = 30.1f,
        maxDb: Float = 55.2f,
        title: String? = null,
        note: String? = null,
    ): MeasurementSummary = MeasurementSummary(
        id = id,
        createdAtEpochMs = createdAt,
        durationMs = durationMs,
        avgDb = avgDb,
        minDb = minDb,
        maxDb = maxDb,
        title = title,
        note = note,
        sparklinePreview = emptyList(),
    )

    @Test
    fun `empty list produces header-only output ending with CRLF`() {
        val output = serialize(emptyList())

        // Header should be present and terminated by CRLF; no data rows.
        assertTrue(output.startsWith("id,createdAt,durationMs,avgDb,minDb,maxDb,title,note"))
        assertTrue(output.endsWith("\r\n"))
        // Exactly one CRLF — header line only.
        assertEquals(1, output.count { it == '\n' })
    }

    @Test
    fun `bytes start with UTF-8 BOM 0xEF 0xBB 0xBF`() {
        val bytes = serializeBytes(emptyList())

        assertTrue(bytes.size >= BOM_BYTES.size)
        assertEquals(BOM_BYTES[0], bytes[0])
        assertEquals(BOM_BYTES[1], bytes[1])
        assertEquals(BOM_BYTES[2], bytes[2])
    }

    @Test
    fun `three measurements serialize as header plus three CRLF-terminated rows`() {
        val output = serialize(
            listOf(
                summary(id = 1L, title = "Office", note = "calm"),
                summary(id = 2L, title = "Street", note = "loud"),
                summary(id = 3L, title = null, note = null),
            ),
        )

        val lines = output.split("\r\n")
        // header + 3 rows + final empty string from trailing CRLF = 5 elements.
        assertEquals(5, lines.size)
        assertTrue(lines[0].startsWith("id,"))
        assertTrue(lines[1].startsWith("1,"))
        assertTrue(lines[2].startsWith("2,"))
        assertTrue(lines[3].startsWith("3,"))
        assertEquals("", lines[4])
    }

    @Test
    fun `title containing comma is wrapped in double quotes`() {
        val output = serialize(listOf(summary(title = "Hello, world", note = null)))
        val dataLine = output.lineSequence().drop(1).first()

        assertTrue(
            dataLine.contains("\"Hello, world\""),
            "expected quoted title in: $dataLine",
        )
    }

    @Test
    fun `note containing double quote is escaped by doubling and wrapping`() {
        val output = serialize(listOf(summary(note = "She said \"hi\"")))
        val dataLine = output.lineSequence().drop(1).first()

        // Inner `"` → `""`, whole field wrapped.
        assertTrue(
            dataLine.contains("\"She said \"\"hi\"\"\""),
            "expected doubled quotes in: $dataLine",
        )
    }

    @Test
    fun `note containing newline is wrapped in quotes preserving the newline`() {
        val output = serialize(listOf(summary(note = "line1\nline2")))
        // Drop the header line + CRLF, then read until the row terminator. The data
        // line itself contains an embedded \n — we cannot rely on lineSequence here.
        val afterHeader = output.substringAfter("\r\n")
        val rowText = afterHeader.removeSuffix("\r\n")

        assertTrue(
            rowText.endsWith("\"line1\nline2\""),
            "expected embedded LF inside quoted note: $rowText",
        )
    }

    @Test
    fun `note containing carriage return is wrapped in quotes`() {
        val output = serialize(listOf(summary(note = "a\rb")))
        val afterHeader = output.substringAfter("\r\n")
        val rowText = afterHeader.removeSuffix("\r\n")

        assertTrue(
            rowText.endsWith("\"a\rb\""),
            "expected embedded CR inside quoted note: $rowText",
        )
    }

    @Test
    fun `null title and null note serialize to empty fields without quoting`() {
        val output = serialize(listOf(summary(id = 7L, title = null, note = null)))
        val dataLine = output.lineSequence().drop(1).first()

        // Last two columns are title,note — both empty → row ends with two consecutive commas + nothing.
        assertTrue(
            dataLine.endsWith(",,"),
            "expected ',,' trailing empty fields, got: $dataLine",
        )
        assertFalse(dataLine.contains("\"\""), "null fields must not be quoted")
    }

    @Test
    fun `avgDb formats with one decimal place using dot as separator`() {
        val output = serialize(listOf(summary(avgDb = 42.567f, minDb = 30.1f, maxDb = 55.299f)))
        val dataLine = output.lineSequence().drop(1).first()

        // Locale.US numeric formatting → "42.6" (rounded), never "42,6".
        assertTrue(
            dataLine.contains(",42.6,"),
            "expected avgDb as 42.6 in row: $dataLine",
        )
        assertFalse(dataLine.contains(",42,6,"), "decimal comma would collide with field separator")
    }

    @Test
    fun `large export of 1000 rows stays under 200 KB`() {
        val rows = (1..1000).map { id ->
            summary(
                id = id.toLong(),
                title = "Замер №$id",
                note = "стабильный шум, без аномалий",
            )
        }
        val bytes = serializeBytes(rows).size

        // Sanity guard from the plan — even with 1000 Cyrillic-heavy rows we stay well
        // under the conservative 200 KB ceiling (plan says ~100 KB; Cyrillic doubles the
        // byte cost over ASCII so 200 KB leaves headroom while still catching pathological
        // formatting regressions like trailing whitespace per row).
        assertTrue(bytes < 200_000, "expected under 200 KB, got $bytes bytes")
    }

    @Test
    fun `header line contains exactly the documented column order`() {
        val output = serialize(emptyList())
        val header = output.lineSequence().first()

        assertEquals("id,createdAt,durationMs,avgDb,minDb,maxDb,title,note", header)
    }

    private companion object {
        val BOM_BYTES: ByteArray = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
