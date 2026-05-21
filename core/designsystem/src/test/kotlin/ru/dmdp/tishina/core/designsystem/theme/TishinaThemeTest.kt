package ru.dmdp.tishina.core.designsystem.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.domain.model.ThemeMode

/**
 * Parametric coverage for [TishinaTheme]'s color-scheme selection — exercises
 * every (themeMode × dynamicColors × API) combination called out in the spec.
 *
 * Strategy: we host a tiny composable that snapshots `MaterialTheme.colorScheme.primary`
 * into a captured slot. After composition we assert the captured value matches
 * the expected branch (static vs dynamic; light vs dark). This avoids brittle
 * pixel comparisons — the contract is "the right `colorScheme` flows down the
 * tree", not "this particular hex shows on screen".
 *
 * The system-dark-mode branch is exercised by forcing `Configuration.uiMode`
 * via a `CompositionLocalProvider(LocalConfiguration)` override so we don't
 * depend on Robolectric's host-OS dark mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TishinaThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `Light + static + API 33 picks static light scheme`() {
        val captured = captureColorScheme(
            themeMode = ThemeMode.Light,
            dynamicColors = false,
            systemDark = false,
        )
        assertEquals("primary must be brand teal", LightColorScheme.primary, captured.primary)
        assertEquals(LightColorScheme.background, captured.background)
    }

    @Test
    fun `Dark + static + API 33 picks static dark scheme`() {
        val captured = captureColorScheme(
            themeMode = ThemeMode.Dark,
            dynamicColors = false,
            systemDark = false,
        )
        assertEquals(DarkColorScheme.primary, captured.primary)
        assertEquals(DarkColorScheme.background, captured.background)
    }

    @Test
    fun `Light + dynamic + API 33 picks dynamic light scheme`() {
        val captured = captureColorScheme(
            themeMode = ThemeMode.Light,
            dynamicColors = true,
            systemDark = false,
        )
        // Dynamic schemes derive from wallpaper, not the static teal. The exact
        // primary depends on Robolectric's stub wallpaper, but it must differ
        // from our static brand color OR match the dynamic API surface — at
        // minimum, the `background` is darker/lighter than the static one
        // because Material You overrides Surface tokens.
        assertNotEquals(
            "dynamicLightColorScheme must replace static LightColorScheme on API 31+",
            LightColorScheme.background,
            captured.background,
        )
    }

    @Test
    fun `Dark + dynamic + API 33 picks dynamic dark scheme`() {
        val captured = captureColorScheme(
            themeMode = ThemeMode.Dark,
            dynamicColors = true,
            systemDark = false,
        )
        assertNotEquals(
            "dynamicDarkColorScheme must replace static DarkColorScheme on API 31+",
            DarkColorScheme.background,
            captured.background,
        )
    }

    @Test
    fun `System + static + system-dark resolves to dark scheme`() {
        val captured = captureColorScheme(
            themeMode = ThemeMode.System,
            dynamicColors = false,
            systemDark = true,
        )
        assertEquals(DarkColorScheme.primary, captured.primary)
    }

    @Test
    fun `System + static + system-light resolves to light scheme`() {
        val captured = captureColorScheme(
            themeMode = ThemeMode.System,
            dynamicColors = false,
            systemDark = false,
        )
        assertEquals(LightColorScheme.primary, captured.primary)
    }

    @Test
    @Config(sdk = [30])
    fun `dynamic colors on API 30 falls back to static without crash`() {
        // No crash: `Build.VERSION.SDK_INT < S`, so dynamic branch is skipped
        // and we end up in static branches. With `themeMode = Light` and
        // `systemDark = false`, that's `LightColorScheme`.
        val captured = captureColorScheme(
            themeMode = ThemeMode.Light,
            dynamicColors = true,
            systemDark = false,
        )
        assertEquals(
            "On API 30 dynamic must fall back to the static LightColorScheme",
            LightColorScheme.primary,
            captured.primary,
        )
    }

    @Test
    @Config(sdk = [30])
    fun `dynamic colors + Dark on API 30 falls back to static dark`() {
        val captured = captureColorScheme(
            themeMode = ThemeMode.Dark,
            dynamicColors = true,
            systemDark = false,
        )
        assertEquals(
            "On API 30 dynamic dark must fall back to the static DarkColorScheme",
            DarkColorScheme.primary,
            captured.primary,
        )
    }

    @Test
    fun `default constructor uses System + dynamic + light defaults`() {
        // No-arg invocation should not crash and should resolve to *some* color
        // scheme. We don't assert a specific palette because the result depends
        // on `isSystemInDarkTheme()`; we only assert the composition completes
        // and captures a non-default primary.
        val captured = captureColorScheme(
            themeMode = null, // marker → use defaults
            dynamicColors = null,
            systemDark = false,
        )
        assertNotEquals(Color.Unspecified, captured.primary)
    }

    private data class CapturedScheme(val primary: Color, val background: Color)

    /**
     * Hosts [TishinaTheme] with the supplied configuration and reads back the
     * resolved `MaterialTheme.colorScheme` from inside the composition. Passing
     * `null` for [themeMode] / [dynamicColors] tests the default-args path.
     */
    private fun captureColorScheme(
        themeMode: ThemeMode?,
        dynamicColors: Boolean?,
        systemDark: Boolean,
    ): CapturedScheme {
        var primary = Color.Unspecified
        var background = Color.Unspecified
        composeTestRule.setContent {
            WithSystemDark(systemDark) {
                when {
                    themeMode == null && dynamicColors == null -> TishinaTheme {
                        primary = MaterialTheme.colorScheme.primary
                        background = MaterialTheme.colorScheme.background
                        Text(text = "anchor")
                    }
                    else -> TishinaTheme(
                        themeMode = themeMode ?: ThemeMode.System,
                        dynamicColors = dynamicColors ?: true,
                    ) {
                        primary = MaterialTheme.colorScheme.primary
                        background = MaterialTheme.colorScheme.background
                        Text(text = "anchor")
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        return CapturedScheme(primary = primary, background = background)
    }

    /**
     * Forces `isSystemInDarkTheme()` (which reads `LocalConfiguration.uiMode`)
     * to the requested value without flipping Robolectric's host configuration.
     */
    @Composable
    private fun WithSystemDark(dark: Boolean, content: @Composable () -> Unit) {
        val base = LocalConfiguration.current
        val overridden = Configuration(base).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        CompositionLocalProvider(
            LocalConfiguration provides overridden,
            LocalContext provides LocalContext.current,
        ) {
            content()
        }
    }
}
