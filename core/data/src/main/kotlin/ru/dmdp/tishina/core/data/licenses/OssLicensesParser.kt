package ru.dmdp.tishina.core.data.licenses

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.dmdp.tishina.core.domain.model.OssLicense

/**
 * Pure JSON-to-model converter for the OSS-license inventory.
 *
 * Splitting parsing from [OssLicensesProviderImpl] is deliberate: the file I/O lives behind
 * an Android `AssetManager` (requires Robolectric), while the format contract belongs to
 * a plain JVM unit test. The whole thing is wrapped in `runCatching` so corrupted assets
 * never crash the About screen (NFR-7 crash-free) — they degrade to an empty list and
 * the surrounding UI shows a "no licenses" fallback.
 *
 * The private `Dto` keeps `kotlinx.serialization` out of `:core:domain` — the domain
 * [OssLicense] is a plain data class with no annotation noise.
 */
object OssLicensesParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(raw: String): List<OssLicense> = runCatching {
        if (raw.isBlank()) {
            emptyList()
        } else {
            json.decodeFromString<List<Dto>>(raw).map { it.toDomain() }
        }
    }.getOrDefault(emptyList())

    @Serializable
    private data class Dto(val name: String, val version: String, val license: String, val url: String) {
        fun toDomain(): OssLicense = OssLicense(name, version, license, url)
    }
}
