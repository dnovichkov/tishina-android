package ru.dmdp.tishina

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertAll
import java.io.File

/**
 * Phase 6 Task 7 — guards `app/src/main/store-metadata/` against silently overflowing the
 * per-store character limits. Title overflows are rejected by the Google Play / RuStore /
 * Samsung APIs at upload time, but a typo in a Cyrillic title is easy to miss by eye and
 * cheap to catch on every PR. Codepoint counts (not byte counts) are used — that is what
 * each store actually validates against.
 */
@DisplayName("Store metadata text files stay within per-store character limits")
class StoreMetadataLengthLimitTest {

    private data class Limit(val relativePath: String, val maxChars: Int)

    private val limits = listOf(
        Limit("google-play/ru-RU/title.txt", GOOGLE_PLAY_TITLE_LIMIT),
        Limit("google-play/ru-RU/short_description.txt", SHORT_DESCRIPTION_LIMIT),
        Limit("google-play/ru-RU/full_description.txt", FULL_DESCRIPTION_LIMIT),
        Limit("google-play/en-US/title.txt", GOOGLE_PLAY_TITLE_LIMIT),
        Limit("google-play/en-US/short_description.txt", SHORT_DESCRIPTION_LIMIT),
        Limit("google-play/en-US/full_description.txt", FULL_DESCRIPTION_LIMIT),
        Limit("rustore/ru-RU/title.txt", RUSTORE_TITLE_LIMIT),
        Limit("rustore/ru-RU/short_description.txt", SHORT_DESCRIPTION_LIMIT),
        Limit("rustore/ru-RU/full_description.txt", FULL_DESCRIPTION_LIMIT),
        Limit("samsung/en-US/title.txt", SAMSUNG_TITLE_LIMIT),
        Limit("samsung/en-US/short_description.txt", SHORT_DESCRIPTION_LIMIT),
        Limit("samsung/en-US/full_description.txt", FULL_DESCRIPTION_LIMIT),
    )

    @TestFactory
    fun `each metadata file exists, is non-empty, and respects its store limit`(): List<DynamicTest> =
        limits.map { limit ->
            DynamicTest.dynamicTest(limit.relativePath) {
                val file = metadataFile(limit.relativePath)
                assertTrue(file.exists(), "${limit.relativePath} must exist under app/src/main/store-metadata/")

                val raw = file.readText(Charsets.UTF_8)
                val content = raw.removePrefix(UTF8_BOM).trim()
                assertFalse(content.isEmpty(), "${limit.relativePath} must not be empty")

                val singleLineForLimit = if (limit.relativePath.endsWith("title.txt") ||
                    limit.relativePath.endsWith("short_description.txt")
                ) {
                    assertFalse(
                        content.contains('\n'),
                        "${limit.relativePath} must be a single line — store APIs reject newlines in titles/short descriptions",
                    )
                    content
                } else {
                    content
                }

                val length = singleLineForLimit.codePointCount(0, singleLineForLimit.length)
                assertTrue(
                    length <= limit.maxChars,
                    "${limit.relativePath} is $length codepoints; max ${limit.maxChars} for this store",
                )
            }
        }

    @TestFactory
    fun `companion docs exist for Data Safety, permissions rationale, ASO keywords`(): List<DynamicTest> = listOf(
        "data-safety.md",
        "permissions-rationale.md",
        "aso-keywords.md",
    ).map { name ->
        DynamicTest.dynamicTest(name) {
            val file = metadataFile(name)
            assertTrue(file.exists(), "$name must exist under app/src/main/store-metadata/")
            assertTrue(file.readText().isNotBlank(), "$name must not be empty")
        }
    }

    @org.junit.jupiter.api.Test
    fun `every full description mentions ASO keyword variants and accuracy disclaimer`() {
        val ruDescriptions = listOf(
            "google-play/ru-RU/full_description.txt",
            "rustore/ru-RU/full_description.txt",
        ).map { metadataFile(it).readText() }

        val enDescriptions = listOf(
            "google-play/en-US/full_description.txt",
            "samsung/en-US/full_description.txt",
        ).map { metadataFile(it).readText() }

        // ASO § 17 — generic keywords belong in the description, never in the title.
        // Spec § 11 — honest accuracy disclaimer keeps store moderation friendly.
        assertAll(
            { ruDescriptions.forEach { assertContainsAny(it, "RU", "шумомер") } },
            { ruDescriptions.forEach { assertContainsAny(it, "RU", "измеритель шума") } },
            { enDescriptions.forEach { assertContainsAny(it, "EN", "sound level meter") } },
            { enDescriptions.forEach { assertContainsAny(it, "EN", "decibel") } },
            { ruDescriptions.forEach { assertContainsAny(it, "RU accuracy disclaimer", *RU_DISCLAIMER_HINTS) } },
            { enDescriptions.forEach { assertContainsAny(it, "EN accuracy disclaimer", *EN_DISCLAIMER_HINTS) } },
        )
    }

    private fun metadataFile(relativePath: String): File =
        File(STORE_METADATA_ROOT, relativePath)

    private fun assertContainsAny(body: String, label: String, vararg needles: String) {
        val match = needles.any { body.contains(it, ignoreCase = true) }
        assertTrue(match, "$label description must contain one of: ${needles.joinToString()}")
    }

    private companion object {
        const val GOOGLE_PLAY_TITLE_LIMIT = 30
        // RuStore tightened the catalogue title limit to 30 in 2025-2026 (was 50 historically).
        // Source: rustore.ru/help/en/developers/publishing-and-verifying-apps/app-publication —
        // "Name: up to 30 characters; the application name must be unique."
        const val RUSTORE_TITLE_LIMIT = 30
        const val SAMSUNG_TITLE_LIMIT = 30
        const val SHORT_DESCRIPTION_LIMIT = 80
        const val FULL_DESCRIPTION_LIMIT = 4000
        const val UTF8_BOM = "\uFEFF"
        val STORE_METADATA_ROOT: File = File("src/main/store-metadata")
        val RU_DISCLAIMER_HINTS = arrayOf(
            "не сертифицирован",
            "ориентировочно",
            "ориентировочный",
        )
        val EN_DISCLAIMER_HINTS = arrayOf(
            "not certified",
            "not a certified",
            "reference only",
            "reference tool",
        )
    }
}
