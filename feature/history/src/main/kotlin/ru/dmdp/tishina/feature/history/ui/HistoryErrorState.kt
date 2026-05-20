package ru.dmdp.tishina.feature.history.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.dmdp.tishina.core.ui.components.AppEmptyState
import ru.dmdp.tishina.feature.history.R

/**
 * History "couldn't read the data" state — shown when the upstream Room flow throws.
 *
 * Distinct from [HistoryEmptyState]: empty means "no rows on disk → start a new measurement"
 * (CTA wired); load-failed means "rows may exist but we couldn't read them → the empty-state
 * CTA would mislead the user". We deliberately omit the CTA here so the user doesn't think
 * the right answer is to overwrite/lose the existing data.
 */
@Composable
fun HistoryErrorState(modifier: Modifier = Modifier) {
    AppEmptyState(
        icon = Icons.Filled.ErrorOutline,
        title = stringResource(R.string.history_load_failed_title),
        description = stringResource(R.string.history_load_failed_description),
        iconContentDescription = stringResource(R.string.history_load_failed_icon_cd),
        modifier = modifier,
    )
}
