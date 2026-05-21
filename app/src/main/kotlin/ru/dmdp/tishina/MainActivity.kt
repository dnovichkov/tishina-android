package ru.dmdp.tishina

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.locale.LocaleSwitcher
import ru.dmdp.tishina.ui.TishinaApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

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
                // LocaleSwitcher::apply so the ViewModel stays platform-agnostic while the
                // Activity owns the AppCompatDelegate side-effect (which triggers the
                // automatic recreate that swaps in the new string resources).
                TishinaApp(
                    windowSizeClass = windowSizeClass,
                    onApplyLocale = LocaleSwitcher::apply,
                )
            }
        }
    }
}
