package ru.dmdp.tishina.core.data.licenses

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Contract for [OssLicensesParser] — the pure JSON-to-model converter that powers
 * the AboutScreen OSS list (FR-21 / NFR-10).
 *
 * Parser is intentionally split from [OssLicensesProvider] so it can be unit-tested
 * on plain JVM without an AssetManager — the only Android coupling for AboutScreen
 * stays inside the I/O wrapper.
 */
@DisplayName("OssLicensesParser — robust JSON-to-model conversion for FR-21")
class OssLicensesParserTest {

    @Test
    fun `parses a valid JSON array into OssLicense list`() {
        val json = """
            [
              {"name":"Kotlin","version":"2.0.21","license":"Apache-2.0","url":"https://kotlinlang.org"},
              {"name":"Hilt","version":"2.55","license":"Apache-2.0","url":"https://dagger.dev/hilt/"}
            ]
        """.trimIndent()

        val result = OssLicensesParser.parse(json)

        assertEquals(2, result.size)
        assertEquals("Kotlin", result[0].name)
        assertEquals("2.0.21", result[0].version)
        assertEquals("Apache-2.0", result[0].license)
        assertEquals("https://kotlinlang.org", result[0].url)
        assertEquals("Hilt", result[1].name)
    }

    @Test
    fun `parses an empty JSON array into empty list`() {
        val result = OssLicensesParser.parse("[]")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `malformed JSON returns empty list without throwing`() {
        val result = OssLicensesParser.parse("{not a valid json array")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `JSON with unknown extra fields is tolerated`() {
        val json = """
            [
              {"name":"Room","version":"2.8.4","license":"Apache-2.0","url":"https://developer.android.com/training/data-storage/room","futureField":"ignored"}
            ]
        """.trimIndent()

        val result = OssLicensesParser.parse(json)

        assertEquals(1, result.size)
        assertEquals("Room", result[0].name)
    }

    @Test
    fun `JSON with missing required field returns empty list`() {
        val json = """[{"name":"X"}]"""

        val result = OssLicensesParser.parse(json)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty input string returns empty list`() {
        val result = OssLicensesParser.parse("")

        assertTrue(result.isEmpty())
    }
}
