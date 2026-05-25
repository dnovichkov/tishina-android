package ru.dmdp.tishina.feature.history.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.dmdp.tishina.feature.history.R

const val HistorySelectionTopBarTestTag: String = "history_selection_top_bar"
const val HistorySelectionDeleteTestTag: String = "history_selection_delete"
const val HistorySelectionCancelTestTag: String = "history_selection_cancel"
const val HistorySelectionSelectAllTestTag: String = "history_selection_select_all"
const val HistorySelectionClearTestTag: String = "history_selection_clear"

/**
 * Action bar shown over the History list while multi-select is active (FR-12).
 *
 * Action ordering follows Material 3 multi-select guidance:
 *  - leading icon: Close (Cancel — leaves selection mode entirely)
 *  - title: "$count selected" via plural-aware string resource
 *  - actions: SelectAll OR ClearSelection (mutually exclusive, depending on
 *    whether everything is already checked) + Delete N
 *
 * Delete is disabled when nothing is selected — it stays present in the layout so
 * the action bar height doesn't reflow as the user toggles selection from 0 → 1 → 0.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
) {
    // pluralStringResource handles count=0 by falling through to the "other" form on ru/en
    // (both plurals carry %d) — same call covers the ClearSelection intermediate state where
    // the selection set is empty but the bar is still up.
    val title = pluralStringResource(
        id = R.plurals.history_selection_topbar_count,
        count = selectedCount,
        selectedCount,
    )
    TopAppBar(
        modifier = modifier.testTag(HistorySelectionTopBarTestTag),
        colors = colors,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.testTag(HistorySelectionCancelTestTag),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.history_cancel_selection_cd),
                )
            }
        },
        actions = {
            // Toggle SelectAll / ClearSelection based on whether everything is checked.
            // When selectedCount == totalCount we surface ClearSelection (the inverse) so
            // the user has a single-tap escape from "all checked". When nothing is checked
            // SelectAll is still shown (`else` branch covers both 0..<totalCount and edge).
            if (selectedCount in 1 until totalCount || selectedCount == 0) {
                IconButton(
                    onClick = onSelectAll,
                    modifier = Modifier.testTag(HistorySelectionSelectAllTestTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SelectAll,
                        contentDescription = stringResource(R.string.history_select_all_cd),
                    )
                }
            } else {
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier.testTag(HistorySelectionClearTestTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = stringResource(R.string.history_clear_selection_cd),
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                enabled = selectedCount > 0,
                modifier = Modifier.testTag(HistorySelectionDeleteTestTag),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.history_bulk_delete_cd),
                )
            }
        },
    )
}
