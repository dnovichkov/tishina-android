package ru.dmdp.tishina.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import ru.dmdp.tishina.core.domain.model.ThemeMode

/**
 * Root Material 3 theme for Tishina.
 *
 * The theme observes the user's [ThemeMode] selection (FR-17) and the
 * dynamic-colors preference (FR-17 again — Material You on Android 12+):
 *
 * - [themeMode] = `System` follows `isSystemInDarkTheme()`; `Light`/`Dark`
 *   force the corresponding scheme regardless of the OS.
 * - [dynamicColors] = `true` on API 31+ pulls
 *   `dynamicLightColorScheme`/`dynamicDarkColorScheme` from the wallpaper.
 *   On API ≤30 it silently falls back to the static brand schemes (the
 *   `Build.VERSION.SDK_INT >= S` guard means there's no crash on legacy).
 *
 * The SPL palette is installed via [LocalSplLevelPalette] so feature modules
 * can pull it through composition without injecting it explicitly.
 */
@Composable
fun TishinaTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColors: Boolean = true,
    splLevelPalette: SplLevelPalette = defaultSplLevelPalette,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    val colorScheme = when {
        dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalSplLevelPalette provides splLevelPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TishinaTypography,
            shapes = TishinaShapes,
            content = content,
        )
    }
}

/**
 * Backwards-compatible overload preserved so existing Roborazzi baselines and
 * screenshot fixtures (`darkTheme = true/false`, `dynamicColor = ...`) keep
 * compiling without forcing a sweeping rename through every feature module.
 *
 * New call sites must use the [ThemeMode]-based primary signature above —
 * `darkTheme = false` collapses both `System (light)` and explicit `Light`
 * into the same boolean, so it cannot express the FR-17 contract on its own.
 */
@Deprecated(
    message = "Use TishinaTheme(themeMode, dynamicColors, ...). " +
        "The boolean darkTheme cannot distinguish ThemeMode.System from ThemeMode.Light.",
    replaceWith = ReplaceWith(
        expression = "TishinaTheme(themeMode = if (darkTheme) ThemeMode.Dark else ThemeMode.Light, " +
            "dynamicColors = dynamicColor, splLevelPalette = splLevelPalette, content = content)",
        imports = ["ru.dmdp.tishina.core.domain.model.ThemeMode"],
    ),
)
@Composable
fun TishinaTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = true,
    splLevelPalette: SplLevelPalette = defaultSplLevelPalette,
    content: @Composable () -> Unit,
) {
    TishinaTheme(
        themeMode = if (darkTheme) ThemeMode.Dark else ThemeMode.Light,
        dynamicColors = dynamicColor,
        splLevelPalette = splLevelPalette,
        content = content,
    )
}
