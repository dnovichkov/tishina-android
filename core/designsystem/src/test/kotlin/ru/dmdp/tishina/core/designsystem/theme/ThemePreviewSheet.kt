package ru.dmdp.tishina.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Test-only fixture used by screenshot tests to exercise typography, color
 * roles, and the SPL level palette in a single deterministic frame.
 *
 * Lives in the test source set because it has no production users — the real
 * preview helper for feature modules lives in :core:testing (Task 6).
 */
@Composable
internal fun ThemePreviewSheet() {
    val palette = LocalSplLevelPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "72",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "dB SPL",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "Tisha",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Sound meter",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SplPaletteStrip(palette)
    }
}

/**
 * Horizontal strip with the 6 SPL palette colors — also used standalone by
 * SplLevelPaletteScreenshotTest.
 */
@Composable
internal fun SplPaletteStrip(palette: SplLevelPalette) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ColorSwatch(palette.veryQuiet)
        ColorSwatch(palette.quiet)
        ColorSwatch(palette.moderate)
        ColorSwatch(palette.loud)
        ColorSwatch(palette.veryLoud)
        ColorSwatch(palette.extreme)
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
    )
}
