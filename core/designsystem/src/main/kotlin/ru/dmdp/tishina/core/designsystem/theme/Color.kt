package ru.dmdp.tishina.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand "decibel" accent — teal #0FB5BA (spec § 6 "Темы").
internal val TishinaTeal = Color(0xFF0FB5BA)
internal val TishinaTealDark = Color(0xFF0A8A8E)
internal val TishinaTealLight = Color(0xFF5AD8DC)
internal val TishinaTealContainer = Color(0xFFCDF5F6)
internal val TishinaOnTealContainer = Color(0xFF002021)

// Neutral surface base for static (non-dynamic) themes. The dark surface is the
// brand "deep navy" used in the launcher icon (#0E2433); the light surface is a
// near-white with a hint of teal to preserve the brand vibe on white backgrounds.
internal val SurfaceDark = Color(0xFF0E2433)
internal val SurfaceLight = Color(0xFFF8FAFB)
internal val SurfaceVariantDark = Color(0xFF1A3344)
internal val SurfaceVariantLight = Color(0xFFE2E8EA)
internal val OnSurfaceDark = Color(0xFFE6EEF1)
internal val OnSurfaceLight = Color(0xFF0E2433)
internal val OnSurfaceVariantDark = Color(0xFFB8C5CB)
internal val OnSurfaceVariantLight = Color(0xFF3F5460)
internal val OutlineVariantDark = Color(0xFF334B5C)
internal val OutlineVariantLight = Color(0xFFCBD5DA)

internal val LightColorScheme = lightColorScheme(
    primary = TishinaTeal,
    onPrimary = Color.White,
    primaryContainer = TishinaTealContainer,
    onPrimaryContainer = TishinaOnTealContainer,
    secondary = TishinaTealDark,
    onSecondary = Color.White,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outlineVariant = OutlineVariantLight,
)

internal val DarkColorScheme = darkColorScheme(
    primary = TishinaTealLight,
    onPrimary = Color(0xFF003739),
    primaryContainer = TishinaTealDark,
    onPrimaryContainer = TishinaTealContainer,
    secondary = TishinaTeal,
    onSecondary = Color.Black,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outlineVariant = OutlineVariantDark,
)
