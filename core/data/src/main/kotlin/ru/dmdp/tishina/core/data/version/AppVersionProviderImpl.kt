package ru.dmdp.tishina.core.data.version

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.dmdp.tishina.core.data.di.IoDispatcher
import ru.dmdp.tishina.core.domain.model.AppVersion
import ru.dmdp.tishina.core.domain.repository.AppVersionProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [AppVersionProvider] reading the live `PackageInfo` via
 * [android.content.pm.PackageManager].
 *
 * **Why not `BuildConfig.VERSION_NAME`?** `BuildConfig` is generated per Gradle module,
 * so a `:core:data` direct read would resolve to the data-module BuildConfig (always
 * `versionCode = 0`). The app's BuildConfig only lives in `:app`. Reading via
 * `PackageManager` gives us the merged value emitted by AAPT into the final APK, which
 * is the same number Play Store / RuStore display.
 *
 * **API 26 → 28 split for versionCode:** `PackageInfo.versionCode` is deprecated since
 * API 28 in favour of `longVersionCode`. We compress back to `Int` because the spec's
 * Play target ceiling fits in `Int.MAX_VALUE` (FR-1 / NFR-1 doesn't anticipate values
 * over 2 billion). On API < 28 we fall back to the deprecated field directly.
 *
 * **Thread discipline:** `PackageManager.getPackageInfo` is an IPC round-trip that
 * StrictMode flags as disk-blocking. The `suspend` interface lets us hop onto
 * [IoDispatcher] here so callers on Main (e.g. `AboutViewModel`) stay non-blocking.
 *
 * **Never-throw contract:** `getPackageInfo` declares `throws NameNotFoundException` —
 * an app cannot fail to resolve its own package on a healthy device, but the contract in
 * [AppVersionProvider] is "always returns a valid value". We wrap with `runCatching` so a
 * pathological device degrades to an empty-name 0-code fallback rather than crashing the
 * About screen (NFR-7 crash-free).
 */
@Singleton
class AppVersionProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AppVersionProvider {

    override suspend fun get(): AppVersion = withContext(ioDispatcher) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
            AppVersion(versionName = info.versionName.orEmpty(), versionCode = code)
        }.getOrElse { throwable ->
            // Only `NameNotFoundException` is declared by `getPackageInfo` — anything else
            // here is a defensive net for an impossible device state.
            if (throwable is PackageManager.NameNotFoundException || throwable is RuntimeException) {
                AppVersion(versionName = "", versionCode = 0)
            } else {
                throw throwable
            }
        }
    }
}
