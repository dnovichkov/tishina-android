package ru.dmdp.tishina.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * Root Material 3 theme for Tisha.
 *
 * - On API 31+ with [dynamicColor] = true, uses the user's wallpaper-derived
 *   color scheme (`dynamicLightColorScheme`/`dynamicDarkColorScheme`).
 * - Otherwise falls back to the static brand schemes built around teal
 *   `#0FB5BA` (see [Color]).
 *
 * The SPL palette is installed via [LocalSplLevelPalette] so feature modules
 * can pull it through composition without injecting it explicitly.
 */
@Composable
fun TishinaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    splLevelPalette: SplLevelPalette = defaultSplLevelPalette,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
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
