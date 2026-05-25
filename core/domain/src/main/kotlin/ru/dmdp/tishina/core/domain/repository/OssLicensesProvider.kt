package ru.dmdp.tishina.core.domain.repository

import ru.dmdp.tishina.core.domain.model.OssLicense

/**
 * Loads the static OSS-license inventory bundled with the APK (FR-21 AboutScreen).
 *
 * The model type [OssLicense] is a pure domain record. The Hilt-bound implementation
 * lives in `:core:data` and reads from `assets/oss_licenses.json` via `AssetManager`.
 *
 * Marked `suspend` so the implementation can hop onto an IO dispatcher — `AssetManager`
 * opens a real file handle on cold launch and JSON parsing is non-trivial CPU work.
 * Implementations must never throw — a corrupted asset returns an empty list, and the
 * AboutScreen renders a "no licenses" placeholder instead of crashing (NFR-7).
 */
fun interface OssLicensesProvider {
    suspend fun load(): List<OssLicense>
}
