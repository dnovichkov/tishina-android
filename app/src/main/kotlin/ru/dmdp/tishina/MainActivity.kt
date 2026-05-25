package ru.dmdp.tishina

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.locale.LocaleSwitcher
import ru.dmdp.tishina.ui.TishinaApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    /**
     * FR-18 — on Android 12 and below `AppCompatDelegate.setApplicationLocales`
     * only applies the new locale to activities tracked in its internal delegate
     * list (subclasses of `AppCompatActivity`). We extend `ComponentActivity`
     * (Compose-first stack), so we wrap the base Context manually with the
     * persisted locale before resources resolve. On API 33+ the platform
     * `LocaleManager` handles per-app locales transparently — no manual wrap.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapWithPersistedLocale(newBase))
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by appViewModel.appearance.collectAsStateWithLifecycle()
            TishinaTheme(
                themeMode = appearance.themeMode,
                dynamicColors = appearance.dynamicColors,
            ) {
                val windowSizeClass = calculateWindowSizeClass(activity = this)
                // FR-18 — SettingsScreen emits ApplyAppLocale as a one-shot UI effect; the
                // Composable surface lifts it as `onApplyLocale`, and we bind it here to
                // [applyLocale] so the ViewModel stays platform-agnostic while the
                // Activity owns the AppCompatDelegate side-effect (and the legacy-API
                // recreate that swaps in the new string resources).
                TishinaApp(
                    windowSizeClass = windowSizeClass,
                    onApplyLocale = ::applyLocale,
                )
            }
        }
    }

    /**
     * FR-18 — persist [locale] via [LocaleSwitcher] and, on Android 12 and
     * below, trigger an activity recreate so [attachBaseContext] re-runs with
     * the new locale list. `AppCompatActivity` would do this automatically via
     * its delegate; we extend [ComponentActivity], so the activity owns the
     * recreate. On API 33+ the platform `LocaleManager` already handles it.
     */
    private fun applyLocale(locale: AppLocale) {
        val previous = AppCompatDelegate.getApplicationLocales()
        LocaleSwitcher.apply(locale)
        val updated = AppCompatDelegate.getApplicationLocales()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && previous != updated) {
            recreate()
        }
    }

    private fun wrapWithPersistedLocale(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        // `AppCompatDelegate.getApplicationLocales()` лениво инициализирует AppCompat'овский
        // SharedPreferences-стор. На повреждённых prefs или неисправном
        // `AppLocalesMetadataHolderService` это бросает — без обёртки активити не стартует.
        // Получая base.context обратно, мы пускаем юзера на системной локали, а DataStore
        // / `AppViewModel` потом отрисует выбранную локаль в Settings; degrade > crash.
        val locales = runCatching { AppCompatDelegate.getApplicationLocales() }.getOrNull()
        if (locales == null || locales.isEmpty) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
        return base.createConfigurationContext(config)
    }
}
