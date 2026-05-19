package ru.dmdp.tishina.core.testing.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme

/**
 * Standard frame for Roborazzi snapshot fixtures across `:core:*` and
 * `:feature:*` modules.
 *
 * Wraps [content] in [TishinaTheme] with a labelled header so every baseline
 * PNG starts with the snapshot's display name — handy when reviewing diffs.
 * `:core:designsystem` intentionally does NOT use this helper: it keeps its
 * own internal `ThemePreviewSheet` to avoid a `:core:testing` -> `:core:designsystem`
 * cycle, since `:core:testing` already depends on the design system.
 */
@Composable
fun PreviewSheet(
    name: String,
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    TishinaTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}
