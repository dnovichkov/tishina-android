package ru.dmdp.tishina.buildlogic.licenses

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

/**
 * Resolved metadata of a single Maven artifact, ready for [OssLicensesGenerator.toJson].
 *
 * Defined as a plain data class so that build-logic stays free of external POJOs.
 */
data class ResolvedPom(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val name: String?,
    val licenseName: String?,
    val licenseUrl: String?,
    val scmUrl: String? = null,
    val projectUrl: String? = null,
)

/**
 * Signalled by [OssLicensesGenerator.parsePom] when input cannot be interpreted.
 *
 * Distinct from [IllegalArgumentException] so the Gradle task layer can catch only
 * generator-specific failures and surface them with actionable context (which artifact
 * failed) without swallowing unrelated bugs.
 */
class OssLicensesGenerationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Phase 6 Task 8 — pure-Kotlin POM→JSON transformer behind the `generateOssLicenses`
 * Gradle task that powers the AboutScreen OSS list (FR-21 / NFR-10).
 *
 * Why a standalone object instead of using `app.cash.licensee`:
 * - licensee's Gradle plugin requires network access on first build and adds a runtime
 *   classpath dependency this project can't take offline-first. We resolve POMs straight
 *   from the Gradle module cache (already populated by `:app:bundleRelease`) using
 *   `Configuration.resolvedConfiguration`, so the generator runs hermetically.
 * - The output shape must stay compatible with `OssLicensesParser` in `:core:data`
 *   (4-field `name/version/license/url` per entry). Keeping the transformer here lets
 *   us pin that contract with regular JUnit tests inside build-logic.
 *
 * XML parsing uses StAX from the JDK (`javax.xml.stream`) — no extra dependencies.
 * JSON serialisation uses `kotlinx-serialization-json` already available in build-logic
 * (no `@Serializable` codegen needed — the runtime tree API is enough).
 */
object OssLicensesGenerator {

    @OptIn(ExperimentalSerializationApi::class)
    private val jsonFormat = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /**
     * Map a free-form `<license><name>` text from a POM to an SPDX short identifier.
     *
     * Returns `null` for unknown licenses — callers decide whether to keep the raw value
     * or skip the entry. We never invent SPDX ids: this protects us against accidentally
     * mis-labelling proprietary code as open-source.
     */
    fun normalizeSpdx(licenseName: String?): String? {
        if (licenseName.isNullOrBlank()) return null
        val key = licenseName.trim().lowercase().replace(Regex("\\s+"), " ")
        return SPDX_MAP[key]
    }

    /**
     * Parse a single POM XML document and extract the metadata fields we care about.
     *
     * Only the first `<license>` entry is kept — multi-license POMs are rare and the
     * About screen has space for a single label. SCM/project URL are recorded so the
     * downstream [toJson] step can resolve the public URL priority.
     */
    @Suppress("CyclomaticComplexMethod", "ComplexMethod", "NestedBlockDepth", "LongMethod", "ThrowsCount")
    fun parsePom(pomXml: String, groupId: String, artifactId: String, version: String): ResolvedPom {
        if (pomXml.isBlank()) {
            throw OssLicensesGenerationException("POM input for $groupId:$artifactId:$version is blank")
        }
        // StAX configuration: no DTD, no external entities — we never fetch network
        // resources during parsing (defence against XXE plus offline-first guarantee).
        // `IS_REPLACING_ENTITY_REFERENCES = true` is the default; we still accumulate
        // text per leaf-path because mixed CHARACTERS/CDATA/whitespace events can arrive
        // in chunks even after entity replacement.
        val factory = XMLInputFactory.newInstance().apply {
            setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
            setProperty(XMLInputFactory.SUPPORT_DTD, false)
            setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false)
        }

        try {
            val reader = factory.createXMLStreamReader(pomXml.byteInputStream(Charsets.UTF_8))

            val textBuffers = mutableMapOf<String, StringBuilder>()
            val path = ArrayDeque<String>()
            var insideFirstLicense = false
            var licensesSeen = 0

            fun bufferKey(): String? = when {
                path.matches("project", "name") -> "projectName"
                path.matches("project", "url") -> "projectUrl"
                path.matches("project", "scm", "url") -> "scmUrl"
                insideFirstLicense && path.matches("project", "licenses", "license", "name") -> "licenseName"
                insideFirstLicense && path.matches("project", "licenses", "license", "url") -> "licenseUrl"
                else -> null
            }

            while (reader.hasNext()) {
                when (reader.next()) {
                    XMLStreamConstants.START_ELEMENT -> {
                        path.addLast(reader.localName)
                        if (path.matches("project", "licenses", "license")) {
                            licensesSeen++
                            insideFirstLicense = licensesSeen == 1
                        }
                    }
                    XMLStreamConstants.CHARACTERS,
                    XMLStreamConstants.CDATA,
                    -> {
                        val key = bufferKey() ?: continue
                        textBuffers.getOrPut(key) { StringBuilder() }.append(reader.text)
                    }
                    XMLStreamConstants.END_ELEMENT -> {
                        if (path.matches("project", "licenses", "license")) {
                            insideFirstLicense = false
                        }
                        path.removeLastOrNull()
                    }
                    else -> Unit
                }
            }
            reader.close()

            return ResolvedPom(
                groupId = groupId,
                artifactId = artifactId,
                version = version,
                name = textBuffers["projectName"]?.normalize(),
                licenseName = textBuffers["licenseName"]?.normalize(),
                licenseUrl = textBuffers["licenseUrl"]?.normalize(),
                scmUrl = textBuffers["scmUrl"]?.normalize(),
                projectUrl = textBuffers["projectUrl"]?.normalize(),
            )
        } catch (ex: OssLicensesGenerationException) {
            throw ex
        } catch (ex: Exception) {
            throw OssLicensesGenerationException(
                "Failed to parse POM for $groupId:$artifactId:$version — ${ex.message}",
                ex,
            )
        }
    }

    /** Collapse whitespace runs to single space and trim — null if the buffer ends up empty. */
    private fun StringBuilder.normalize(): String? {
        val compact = toString().replace(Regex("\\s+"), " ").trim()
        return compact.takeIf { it.isNotEmpty() }
    }

    /**
     * Serialise a list of resolved POMs into the 4-field `oss_licenses.json` shape
     * consumed by `OssLicensesParser` in `:core:data`.
     *
     * Rules:
     * - Entries without any license info are SKIPPED (we never publish "Unknown"; if a
     *   dependency lacks license metadata we want it absent from the About screen so the
     *   missing entry is noticed).
     * - Same `groupId:artifactId` is deduplicated, highest version wins (string-lexical
     *   ordering is enough for our SemVer-shaped versions; CI build is the source of
     *   truth so we never collide on patch-level identical versions).
     * - Sorted by display-name case-insensitive for stable diffs.
     * - URL priority: `scm.url` > `<project><url>` > first `<license><url>` > "".
     */
    fun toJson(items: List<ResolvedPom>): String {
        val deduped = items
            .filter { !it.licenseName.isNullOrBlank() }
            .groupBy { "${it.groupId}:${it.artifactId}" }
            .map { (_, group) -> group.maxBy { it.version } }

        val sorted = deduped.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName() })

        val array = JsonArray(
            sorted.map { pom ->
                val licenseLabel = normalizeSpdx(pom.licenseName) ?: pom.licenseName!!.trim()
                JsonObject(
                    linkedMapOf(
                        "name" to JsonPrimitive(pom.displayName()),
                        "version" to JsonPrimitive(pom.version),
                        "license" to JsonPrimitive(licenseLabel),
                        "url" to JsonPrimitive(pom.publicUrl()),
                    ),
                )
            },
        )

        return jsonFormat.encodeToString(JsonArray.serializer(), array) + "\n"
    }

    private fun ResolvedPom.displayName(): String = name?.trim()?.takeIf { it.isNotEmpty() } ?: "$groupId:$artifactId"

    private fun ResolvedPom.publicUrl(): String =
        scmUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: projectUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: licenseUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: ""

    private fun ArrayDeque<String>.matches(vararg segments: String): Boolean {
        if (size != segments.size) return false
        return segments.withIndex().all { (idx, expected) -> this.elementAt(idx) == expected }
    }

    /**
     * Curated SPDX-name mapping. The key is the license name lowercased + whitespace
     * compacted, the value is the SPDX short identifier.
     *
     * We intentionally do NOT pull a giant dictionary in — only license families that
     * the Tishina dependency tree actually ships. Adding a new dep with an unknown
     * license falls through to "preserve raw text" so we still publish *something*
     * and the omission is visible in the next code review.
     */
    private val SPDX_MAP: Map<String, String> = mapOf(
        "apache 2.0" to "Apache-2.0",
        "apache 2" to "Apache-2.0",
        "apache-2.0" to "Apache-2.0",
        "apache license" to "Apache-2.0",
        "apache license 2.0" to "Apache-2.0",
        "apache license, version 2.0" to "Apache-2.0",
        "apache software license, version 2.0" to "Apache-2.0",
        "the apache license, version 2.0" to "Apache-2.0",
        "the apache software license, version 2.0" to "Apache-2.0",
        "mit" to "MIT",
        "mit license" to "MIT",
        "the mit license" to "MIT",
        "bsd 2-clause license" to "BSD-2-Clause",
        "bsd-2-clause" to "BSD-2-Clause",
        "bsd 3-clause license" to "BSD-3-Clause",
        "bsd-3-clause" to "BSD-3-Clause",
        "the bsd 3-clause license" to "BSD-3-Clause",
        "epl-2.0" to "EPL-2.0",
        "eclipse public license v 2.0" to "EPL-2.0",
        "eclipse public license v2.0" to "EPL-2.0",
        "eclipse public license, version 2.0" to "EPL-2.0",
        "eclipse public license 2.0" to "EPL-2.0",
        "epl-1.0" to "EPL-1.0",
        "eclipse public license - v 1.0" to "EPL-1.0",
        "eclipse public license, version 1.0" to "EPL-1.0",
        "eclipse public license 1.0" to "EPL-1.0",
        "lgpl-2.1" to "LGPL-2.1",
        "gnu lesser general public license, version 2.1" to "LGPL-2.1",
        "cc0-1.0" to "CC0-1.0",
        "cc0 1.0 universal" to "CC0-1.0",
    )
}
