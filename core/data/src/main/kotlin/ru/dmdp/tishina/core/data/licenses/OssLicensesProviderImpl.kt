package ru.dmdp.tishina.core.data.licenses

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.dmdp.tishina.core.domain.model.OssLicense
import ru.dmdp.tishina.core.domain.repository.OssLicensesProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [OssLicensesProvider] reading `assets/oss_licenses.json` from the APK.
 *
 * The asset lives under `:app/src/main/assets/` so that it is bundled into the final
 * release APK once and only once (per the Gradle merge order, the application module's
 * assets win — keeping curation in a single place).
 *
 * **Failure handling:** `runCatching` traps both `IOException` (missing asset) and any
 * parser errors, returning an empty list so AboutScreen falls back to the placeholder.
 * The cost of a silently empty list is preferable to crashing on a malformed JSON in
 * the field — Phase 5 ships a manually curated file, but Phase Release may switch to
 * a Gradle-generated one where transient errors are realistic.
 */
@Singleton
class OssLicensesProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : OssLicensesProvider {

    override fun load(): List<OssLicense> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
            OssLicensesParser.parse(reader.readText())
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val ASSET_NAME = "oss_licenses.json"
    }
}
