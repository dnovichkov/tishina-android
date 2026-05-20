package ru.dmdp.tishina.feature.history.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.dmdp.tishina.core.ui.components.AppEmptyState
import ru.dmdp.tishina.feature.history.R

/**
 * History "no measurements yet" state (FR-9 fallback path).
 *
 * Wraps the shared [AppEmptyState] with history-specific copy + the `graphic_eq` icon —
 * the same glyph as the Measure tab to keep the visual story consistent (a wave that means
 * "sound measurement"). The CTA routes to the Measure tab so a first-time user can start
 * a session immediately instead of hunting for the bottom nav.
 */
@Composable
fun HistoryEmptyState(
    onNavigateToMeasure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppEmptyState(
        icon = Icons.Filled.GraphicEq,
        title = stringResource(R.string.history_empty_title),
        description = stringResource(R.string.history_empty_description),
        iconContentDescription = stringResource(R.string.history_empty_icon_cd),
        ctaLabel = stringResource(R.string.history_empty_cta),
        onCtaClick = onNavigateToMeasure,
        modifier = modifier,
    )
}
