package ru.dmdp.tishina.core.data.licenses

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.dmdp.tishina.core.domain.model.OssLicense

/**
 * Pure JSON-to-model converter for the OSS-license inventory.
 *
 * Splitting parsing from [OssLicensesProviderImpl] is deliberate: the file I/O lives behind
 * an Android `AssetManager` (requires Robolectric), while the format contract belongs to
 * a plain JVM unit test. Corrupted assets never crash the About screen (NFR-7 crash-free) —
 * they degrade to an empty list and the surrounding UI shows a "no licenses" fallback.
 *
 * The private `Dto` keeps `kotlinx.serialization` out of `:core:domain` — the domain
 * [OssLicense] is a plain data class with no annotation noise.
 *
 * Cancellation discipline: this function is non-suspending and pure CPU, but it is called
 * from a `suspend` provider on an IO dispatcher. We use an explicit `try/catch` that
 * rethrows [CancellationException] so structured cancellation propagates through. Wrapping
 * the whole block in `runCatching` would catch and discard the cancellation along with
 * everything else — the exact anti-pattern that [OssLicensesProviderImpl] also avoids.
 */
object OssLicensesParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    @Suppress("TooGenericExceptionCaught")
    fun parse(raw: String): List<OssLicense> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<Dto>>(raw).map { it.toDomain() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            emptyList()
        }
    }

    @Serializable
    private data class Dto(val name: String, val version: String, val license: String, val url: String) {
        fun toDomain(): OssLicense = OssLicense(name, version, license, url)
    }
}
