package ru.dmdp.tishina.buildlogic.licenses

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Contract for [OssLicensesGenerator] — the pure-Kotlin POM→JSON transformer that powers
 * the `generateOssLicenses` Gradle task (Phase 6 Task 8).
 *
 * The generator is split from the Gradle wiring so that the format contract (POM XML →
 * `oss_licenses.json` shape consumed by `OssLicensesParser` in `:core:data`) can be
 * verified with plain JUnit on the JVM, without spinning up a Gradle TestKit project.
 */
@DisplayName("OssLicensesGenerator — POM-to-JSON transformer for FR-21 / NFR-10")
class OssLicensesGeneratorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    @DisplayName("parsePom extracts name, license, scm.url from a typical Apache-2.0 POM")
    fun parsePom_apache_typicalPom() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <name>AndroidX Core</name>
              <description>Provides backward-compatible implementations of new platform features.</description>
              <licenses>
                <license>
                  <name>The Apache Software License, Version 2.0</name>
                  <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
                </license>
              </licenses>
              <scm>
                <url>https://cs.android.com/androidx/platform/frameworks/support</url>
              </scm>
            </project>
        """.trimIndent()

        val result = OssLicensesGenerator.parsePom(pom, "androidx.core", "core-ktx", "1.13.1")

        assertEquals("androidx.core", result.groupId)
        assertEquals("core-ktx", result.artifactId)
        assertEquals("1.13.1", result.version)
        assertEquals("AndroidX Core", result.name)
        assertEquals("The Apache Software License, Version 2.0", result.licenseName)
        assertEquals("http://www.apache.org/licenses/LICENSE-2.0.txt", result.licenseUrl)
        assertEquals("https://cs.android.com/androidx/platform/frameworks/support", result.scmUrl)
    }

    @Test
    @DisplayName("parsePom prefers <name> over <artifactId> for human-readable label")
    fun parsePom_preferNameOverArtifactId() {
        val pom = """
            <project>
              <name>Hilt Android</name>
              <licenses>
                <license>
                  <name>Apache License 2.0</name>
                </license>
              </licenses>
            </project>
        """.trimIndent()

        val result = OssLicensesGenerator.parsePom(pom, "com.google.dagger", "hilt-android", "2.55")

        assertEquals("Hilt Android", result.name)
    }

    @Test
    @DisplayName("parsePom falls back to null name when <name> missing")
    fun parsePom_missingNameField() {
        val pom = """
            <project>
              <licenses>
                <license>
                  <name>MIT</name>
                </license>
              </licenses>
            </project>
        """.trimIndent()

        val result = OssLicensesGenerator.parsePom(pom, "org.example", "lib", "1.0")

        assertNull(result.name)
        assertEquals("MIT", result.licenseName)
    }

    @Test
    @DisplayName("parsePom returns null licenseName when <licenses> block absent")
    fun parsePom_missingLicensesBlock() {
        val pom = """
            <project>
              <name>Mystery Lib</name>
              <url>https://example.com</url>
            </project>
        """.trimIndent()

        val result = OssLicensesGenerator.parsePom(pom, "org.example", "mystery", "1.0")

        assertNull(result.licenseName)
        assertNull(result.licenseUrl)
        assertEquals("https://example.com", result.projectUrl)
    }

    @Test
    @DisplayName("parsePom takes only the first <license> when several declared")
    fun parsePom_multipleLicenses_takesFirst() {
        val pom = """
            <project>
              <licenses>
                <license>
                  <name>Apache-2.0</name>
                  <url>https://www.apache.org/licenses/LICENSE-2.0</url>
                </license>
                <license>
                  <name>MIT</name>
                  <url>https://opensource.org/licenses/MIT</url>
                </license>
              </licenses>
            </project>
        """.trimIndent()

        val result = OssLicensesGenerator.parsePom(pom, "org.example", "dual", "1.0")

        assertEquals("Apache-2.0", result.licenseName)
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0", result.licenseUrl)
    }

    @Test
    @DisplayName("parsePom decodes XML entities and trims whitespace")
    fun parsePom_decodesEntities() {
        val pom = """
            <project>
              <name>  Foo &amp; Bar  </name>
              <licenses>
                <license>
                  <name>
                    Apache License 2.0
                  </name>
                </license>
              </licenses>
            </project>
        """.trimIndent()

        val result = OssLicensesGenerator.parsePom(pom, "g", "a", "1")

        assertEquals("Foo & Bar", result.name)
        assertEquals("Apache License 2.0", result.licenseName)
    }

    @Test
    @DisplayName("parsePom rejects empty / blank input fast-fails")
    fun parsePom_emptyInput_throws() {
        assertThrows(OssLicensesGenerationException::class.java) {
            OssLicensesGenerator.parsePom("   ", "g", "a", "1")
        }
    }

    @Test
    @DisplayName("normalizeSpdx maps common license name variants to SPDX identifiers")
    fun normalizeSpdx_mapsCommonLicenses() {
        assertEquals("Apache-2.0", OssLicensesGenerator.normalizeSpdx("The Apache Software License, Version 2.0"))
        assertEquals("Apache-2.0", OssLicensesGenerator.normalizeSpdx("Apache 2.0"))
        assertEquals("Apache-2.0", OssLicensesGenerator.normalizeSpdx("Apache License 2.0"))
        assertEquals("Apache-2.0", OssLicensesGenerator.normalizeSpdx("Apache License, Version 2.0"))
        assertEquals("Apache-2.0", OssLicensesGenerator.normalizeSpdx("Apache-2.0"))
        assertEquals("MIT", OssLicensesGenerator.normalizeSpdx("MIT License"))
        assertEquals("MIT", OssLicensesGenerator.normalizeSpdx("MIT"))
        assertEquals("BSD-2-Clause", OssLicensesGenerator.normalizeSpdx("BSD 2-Clause License"))
        assertEquals("BSD-3-Clause", OssLicensesGenerator.normalizeSpdx("BSD 3-Clause License"))
        assertEquals("EPL-2.0", OssLicensesGenerator.normalizeSpdx("Eclipse Public License v2.0"))
        assertEquals("EPL-2.0", OssLicensesGenerator.normalizeSpdx("Eclipse Public License, Version 2.0"))
        assertEquals("EPL-1.0", OssLicensesGenerator.normalizeSpdx("Eclipse Public License - v 1.0"))
    }

    @Test
    @DisplayName("normalizeSpdx returns null for unknown license names so caller can decide policy")
    fun normalizeSpdx_unknownReturnsNull() {
        assertNull(OssLicensesGenerator.normalizeSpdx("Proprietary Closed Garbage"))
        assertNull(OssLicensesGenerator.normalizeSpdx(""))
        assertNull(OssLicensesGenerator.normalizeSpdx(null))
    }

    @Test
    @DisplayName("toJson produces array sorted by display name (case-insensitive)")
    fun toJson_sortsCaseInsensitive() {
        val poms = listOf(
            pom("z.lib", "z", "1", name = "ZeroLib", licenseName = "Apache-2.0"),
            pom("a.lib", "a", "1", name = "AlphaLib", licenseName = "Apache-2.0"),
            pom("m.lib", "m", "1", name = "midLib", licenseName = "MIT"),
        )

        val out = OssLicensesGenerator.toJson(poms)

        val arr = json.parseToJsonElement(out).jsonArray
        assertEquals(3, arr.size)
        assertEquals("AlphaLib", arr[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("midLib", arr[1].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("ZeroLib", arr[2].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("toJson deduplicates same groupId:artifactId (keeps highest version by lexical order)")
    fun toJson_deduplicates() {
        val poms = listOf(
            pom("g", "a", "1.0.0", name = "Lib A", licenseName = "Apache-2.0"),
            pom("g", "a", "1.0.1", name = "Lib A", licenseName = "Apache-2.0"),
            pom("g", "b", "1.0.0", name = "Lib B", licenseName = "MIT"),
        )

        val out = OssLicensesGenerator.toJson(poms)

        val arr = json.parseToJsonElement(out).jsonArray
        assertEquals(2, arr.size)
        val libA = arr.first { it.jsonObject["name"]!!.jsonPrimitive.content == "Lib A" }
        assertEquals("1.0.1", libA.jsonObject["version"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("toJson emits the legacy 4-field shape consumed by OssLicensesParser")
    fun toJson_legacyShape() {
        val poms = listOf(
            pom(
                "androidx.core", "core-ktx", "1.13.1",
                name = "AndroidX Core",
                licenseName = "The Apache Software License, Version 2.0",
                licenseUrl = "http://www.apache.org/licenses/LICENSE-2.0.txt",
                scmUrl = "https://cs.android.com/androidx/platform/frameworks/support",
            ),
        )

        val out = OssLicensesGenerator.toJson(poms)

        val entry = json.parseToJsonElement(out).jsonArray[0].jsonObject
        assertEquals(setOf("name", "version", "license", "url"), entry.keys)
        assertEquals("AndroidX Core", entry["name"]!!.jsonPrimitive.content)
        assertEquals("1.13.1", entry["version"]!!.jsonPrimitive.content)
        assertEquals("Apache-2.0", entry["license"]!!.jsonPrimitive.content)
        assertEquals(
            "https://cs.android.com/androidx/platform/frameworks/support",
            entry["url"]!!.jsonPrimitive.content,
        )
    }

    @Test
    @DisplayName("toJson falls back to coordinate when <name> missing")
    fun toJson_coordinateFallback() {
        val poms = listOf(pom("org.example", "lib", "1.0", licenseName = "MIT"))

        val out = OssLicensesGenerator.toJson(poms)

        val entry = json.parseToJsonElement(out).jsonArray[0].jsonObject
        assertEquals("org.example:lib", entry["name"]!!.jsonPrimitive.content)
        assertEquals("MIT", entry["license"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("toJson uses scmUrl > projectUrl > licenseUrl when picking the public URL")
    fun toJson_urlPriority() {
        val withScm = pom(
            "g", "a", "1",
            name = "A",
            licenseName = "Apache-2.0",
            licenseUrl = "https://license/A",
            scmUrl = "https://scm/A",
            projectUrl = "https://proj/A",
        )
        val withProj = pom(
            "g", "b", "1",
            name = "B",
            licenseName = "Apache-2.0",
            licenseUrl = "https://license/B",
            projectUrl = "https://proj/B",
        )
        val withLicenseOnly = pom(
            "g", "c", "1",
            name = "C",
            licenseName = "Apache-2.0",
            licenseUrl = "https://license/C",
        )
        val withNothing = pom(
            "g", "d", "1",
            name = "D",
            licenseName = "Apache-2.0",
        )

        val out = OssLicensesGenerator.toJson(listOf(withScm, withProj, withLicenseOnly, withNothing))

        val arr = json.parseToJsonElement(out).jsonArray
        val byName: (String) -> JsonObject = { needle ->
            arr.map { it.jsonObject }.first { it["name"]!!.jsonPrimitive.content == needle }
        }
        assertEquals("https://scm/A", byName("A")["url"]!!.jsonPrimitive.content)
        assertEquals("https://proj/B", byName("B")["url"]!!.jsonPrimitive.content)
        assertEquals("https://license/C", byName("C")["url"]!!.jsonPrimitive.content)
        assertEquals("", byName("D")["url"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("toJson preserves raw license name when no SPDX mapping exists (graceful degradation)")
    fun toJson_keepsRawLicenseIfUnknown() {
        val poms = listOf(pom("g", "a", "1", name = "Unusual", licenseName = "Custom Garage License"))

        val out = OssLicensesGenerator.toJson(poms)

        val entry = json.parseToJsonElement(out).jsonArray[0].jsonObject
        assertEquals("Custom Garage License", entry["license"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("toJson skips entries with no license info (defensive — never publishes 'Unknown')")
    fun toJson_skipsEntriesWithoutAnyLicense() {
        val poms = listOf(
            pom("g", "with-license", "1", name = "WithLicense", licenseName = "Apache-2.0"),
            pom("g", "no-license", "1", name = "NoLicense"),
        )

        val out = OssLicensesGenerator.toJson(poms)

        val arr = json.parseToJsonElement(out).jsonArray
        assertEquals(1, arr.size)
        assertEquals("WithLicense", arr[0].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("toJson output is pretty-printed JSON (2-space indent + trailing newline) for diff-friendliness")
    fun toJson_prettyPrinted() {
        val poms = listOf(pom("g", "a", "1", name = "A", licenseName = "Apache-2.0"))

        val out = OssLicensesGenerator.toJson(poms)

        assertTrue(out.startsWith("["), "must start with '['")
        assertTrue(out.contains("\n  {"), "expected 2-space indented objects")
        assertTrue(out.endsWith("\n"), "must end with a trailing newline so git diff stays clean")
    }

    @Test
    @DisplayName("toJson output is parseable as a non-empty JsonArray")
    fun toJson_isValidJsonArray() {
        val poms = listOf(
            pom("g", "a", "1", name = "A", licenseName = "Apache-2.0"),
            pom("g", "b", "1", name = "B", licenseName = "MIT"),
        )

        val out = OssLicensesGenerator.toJson(poms)

        val element = json.parseToJsonElement(out)
        assertNotNull(element as? JsonArray)
        assertEquals(2, element.jsonArray.size)
    }

    @Suppress("LongParameterList")
    private fun pom(
        groupId: String,
        artifactId: String,
        version: String,
        name: String? = null,
        licenseName: String? = null,
        licenseUrl: String? = null,
        scmUrl: String? = null,
        projectUrl: String? = null,
    ): ResolvedPom = ResolvedPom(
        groupId = groupId,
        artifactId = artifactId,
        version = version,
        name = name,
        licenseName = licenseName,
        licenseUrl = licenseUrl,
        scmUrl = scmUrl,
        projectUrl = projectUrl,
    )
}
