package ru.dmdp.tishina.core.data.version

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
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
 */
@Singleton
class AppVersionProviderImpl @Inject constructor(@ApplicationContext private val context: Context) : AppVersionProvider {

    override fun get(): AppVersion {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
        return AppVersion(versionName = info.versionName.orEmpty(), versionCode = code)
    }
}
