package ru.dmdp.tishina.core.data.licenses

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.dmdp.tishina.core.data.di.IoDispatcher
import ru.dmdp.tishina.core.domain.model.OssLicense
import ru.dmdp.tishina.core.domain.repository.OssLicensesProvider
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [OssLicensesProvider] reading `assets/oss_licenses.json` from the APK.
 *
 * The asset lives under `:app/src/main/assets/` so that it is bundled into the final
 * release APK once and only once (per the Gradle merge order, the application module's
 * assets win — keeping curation in a single place).
 *
 * **Thread discipline:** `AssetManager.open` is a real file-system call and JSON parsing
 * isn't free. The `suspend` interface lets us hop onto [IoDispatcher] here so the
 * AboutViewModel's cold-launch resolve doesn't block Main.
 *
 * **Failure handling:** we catch [IOException] (missing asset / unreadable stream) here
 * and return an empty list so AboutScreen falls back to the placeholder. Malformed JSON
 * is the parser's concern: [OssLicensesParser.parse] already guarantees a never-throw
 * contract via its own crash-free wrapper (covered by `OssLicensesParserTest`), so there
 * is nothing for `SerializationException` to propagate up to this layer. We deliberately
 * do NOT wrap the whole block in `runCatching` because it would also swallow
 * `CancellationException` and break structured concurrency.
 */
@Singleton
class OssLicensesProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : OssLicensesProvider {

    @Suppress("SwallowedException") // never-throw contract — see class kdoc
    override suspend fun load(): List<OssLicense> = withContext(ioDispatcher) {
        try {
            context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
                OssLicensesParser.parse(reader.readText())
            }
        } catch (io: IOException) {
            emptyList()
        }
    }

    private companion object {
        const val ASSET_NAME = "oss_licenses.json"
    }
}
