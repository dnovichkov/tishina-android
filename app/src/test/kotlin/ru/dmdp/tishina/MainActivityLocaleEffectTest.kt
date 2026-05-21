package ru.dmdp.tishina

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.compose.rememberNavController
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.locale.LocaleSwitcher
import ru.dmdp.tishina.navigation.TopLevelDestination
import ru.dmdp.tishina.testutils.HistoryScreenTestStub
import ru.dmdp.tishina.testutils.MeasureScreenTestStub
import ru.dmdp.tishina.ui.TishinaApp
import ru.dmdp.tishina.ui.navigationItemTestTag

/**
 * FR-18 integration contract — verifies that the `onApplyLocale` callback
 * `MainActivity` plugs into [TishinaApp] really lands on [LocaleSwitcher],
 * so emitting `SettingsUiEffect.ApplyAppLocale(...)` from the Settings
 * screen propagates all the way down to `AppCompatDelegate`.
 *
 * The Settings destination itself is replaced with a stub `settingsContent`
 * slot so the test does not need a Hilt-injected `SettingsViewModel` /
 * DataStore — we only care that `TishinaApp` forwards its `onApplyLocale`
 * lambda into the Settings slot.
 *
 * `AppCompatDelegate.setApplicationLocales` mutates global state, so each
 * test restores the previously-applied list in [tearDown].
 */
@RunWith(RobolectricTestRunner::class)
// Pin to API 32 so AppCompatDelegate.setApplicationLocales round-trips through its
// own SharedPreferences fallback instead of the API 33+ LocaleManager system service,
// which Robolectric stubs but never persists writes against.
@Config(sdk = [Build.VERSION_CODES.S_V2])
class MainActivityLocaleEffectTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val savedLocales: LocaleListCompat = AppCompatDelegate.getApplicationLocales()

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(savedLocales)
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `onApplyLocale plumbed through TishinaApp invokes LocaleSwitcher`() {
        val capturedApplyLocale = setUpAppWithCapturingSettingsSlot()

        // The slot composes only once Settings is the active entry — drive the bottom
        // navigation to land on it.
        composeTestRule
            .onNodeWithTag(navigationItemTestTag(TopLevelDestination.Settings))
            .performClick()
        composeTestRule.waitForIdle()

        val applyLocale = requireNotNull(capturedApplyLocale.get()) {
            "TishinaApp did not pass onApplyLocale into the settingsContent slot"
        }
        applyLocale(AppLocale.Russian)

        assertEquals("ru", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `onApplyLocale handles System reset back to the OS locale list`() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        val capturedApplyLocale = setUpAppWithCapturingSettingsSlot()

        composeTestRule
            .onNodeWithTag(navigationItemTestTag(TopLevelDestination.Settings))
            .performClick()
        composeTestRule.waitForIdle()

        val applyLocale = requireNotNull(capturedApplyLocale.get())
        applyLocale(AppLocale.System)

        assertEquals("", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun setUpAppWithCapturingSettingsSlot(): SlotHandle {
        val handle = SlotHandle()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val navController = rememberNavController()
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
                // Mirrors the production wiring in MainActivity — onApplyLocale is bound
                // to LocaleSwitcher::apply. The settingsContent slot captures the lambda
                // for direct invocation so we don't need a Hilt-backed SettingsViewModel.
                TishinaApp(
                    windowSizeClass = sizeClass,
                    navController = navController,
                    onApplyLocale = LocaleSwitcher::apply,
                    measureContent = { MeasureScreenTestStub() },
                    historyContent = { _, _ -> HistoryScreenTestStub() },
                    settingsContent = { onApplyLocale -> handle.set(onApplyLocale) },
                )
            }
        }
        composeTestRule.waitForIdle()
        return handle
    }

    /** Mutable holder so the captured lambda survives across recompositions. */
    private class SlotHandle {
        private var value: ((AppLocale) -> Unit)? = null

        fun set(callback: (AppLocale) -> Unit) {
            value = callback
        }

        fun get(): ((AppLocale) -> Unit)? = value
    }
}
