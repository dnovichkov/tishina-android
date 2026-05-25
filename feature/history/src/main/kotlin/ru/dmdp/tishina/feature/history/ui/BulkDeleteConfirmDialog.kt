package ru.dmdp.tishina.feature.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.feature.history.R

const val BulkDeleteConfirmDialogTestTag: String = "bulk_delete_confirm_dialog"
const val BulkDeleteConfirmDialogConfirmTestTag: String = "bulk_delete_confirm_dialog_confirm"
const val BulkDeleteConfirmDialogCancelTestTag: String = "bulk_delete_confirm_dialog_cancel"

/**
 * Production-facing confirm dialog before a bulk soft-delete (FR-12 / NFR-12).
 *
 * Title is pluralized through `history_bulk_confirm_title` so Russian's one/few/many forms
 * resolve correctly. Body warns about the 5 s Undo window — the actual commit happens on a
 * VM-owned timer; the dialog only guards against an accidental tap before scheduling.
 *
 * Wraps [BulkDeleteConfirmDialogContent] in [AlertDialog] for production; tests render the
 * inner Surface variant directly because Material 3 AlertDialog's sub-Window doesn't reliably
 * settle under Robolectric (same pattern as MeasureSaveDialog).
 */
@Composable
fun BulkDeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(BulkDeleteConfirmDialogTestTag),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = pluralStringResource(
                    id = R.plurals.history_bulk_confirm_title,
                    count = count,
                    count,
                ),
            )
        },
        text = {
            Text(text = stringResource(R.string.history_bulk_confirm_body))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(BulkDeleteConfirmDialogConfirmTestTag),
            ) {
                Text(stringResource(R.string.history_bulk_confirm_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(BulkDeleteConfirmDialogCancelTestTag),
            ) {
                Text(stringResource(R.string.history_bulk_cancel_action))
            }
        },
    )
}

/**
 * Window-less variant used by Roborazzi screenshot tests. Identical visuals minus the
 * AlertDialog scrim/sub-window.
 */
@Composable
internal fun BulkDeleteConfirmDialogContent(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(BulkDeleteConfirmDialogTestTag),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = pluralStringResource(
                    id = R.plurals.history_bulk_confirm_title,
                    count = count,
                    count,
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.history_bulk_confirm_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(BulkDeleteConfirmDialogCancelTestTag),
                ) {
                    Text(stringResource(R.string.history_bulk_cancel_action))
                }
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.testTag(BulkDeleteConfirmDialogConfirmTestTag),
                ) {
                    Text(stringResource(R.string.history_bulk_confirm_action))
                }
            }
        }
    }
}
