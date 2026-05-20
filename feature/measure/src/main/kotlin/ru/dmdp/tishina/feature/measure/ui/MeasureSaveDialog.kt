package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.feature.measure.R

const val MeasureSaveDialogTestTag: String = "measure_save_dialog"
const val MeasureSaveDialogTitleFieldTestTag: String = "measure_save_dialog_title_field"
const val MeasureSaveDialogNoteFieldTestTag: String = "measure_save_dialog_note_field"
const val MeasureSaveDialogConfirmTestTag: String = "measure_save_dialog_confirm"
const val MeasureSaveDialogCancelTestTag: String = "measure_save_dialog_cancel"

/**
 * Production-facing Save dialog (FR-6). Wraps [MeasureSaveDialogContent] in Material 3's
 * [AlertDialog] so the dim/scrim and platform Window semantics are correct on a real device.
 *
 * Tests target [MeasureSaveDialogContent] directly — Material 3's `AlertDialog` renders into a
 * sub-Window that, combined with `OutlinedTextField`'s built-in animations, never settles under
 * Robolectric's idling strategy (`AppNotIdleException`). The content-only path is identical
 * apart from the surrounding Window.
 */
@Composable
fun MeasureSaveDialog(
    onConfirm: (title: String?, note: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialTitle: String = "",
    initialNote: String = "",
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    val titleOverflow = title.length > NewMeasurement.MAX_TITLE_LENGTH
    val noteOverflow = note.length > NewMeasurement.MAX_NOTE_LENGTH
    val canSave = !titleOverflow && !noteOverflow

    AlertDialog(
        modifier = modifier.testTag(MeasureSaveDialogTestTag),
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.measure_save_dialog_title)) },
        text = {
            MeasureSaveDialogFormBody(
                title = title,
                onTitleChange = { title = it },
                titleOverflow = titleOverflow,
                note = note,
                onNoteChange = { note = it },
                noteOverflow = noteOverflow,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.ifEmpty { null }, note.ifEmpty { null }) },
                enabled = canSave,
                modifier = Modifier.testTag(MeasureSaveDialogConfirmTestTag),
            ) {
                Text(stringResource(id = R.string.measure_save_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(MeasureSaveDialogCancelTestTag),
            ) {
                Text(stringResource(id = R.string.measure_save_cancel))
            }
        },
    )
}

/**
 * Inline (no Window) Save dialog content. Same visuals as [MeasureSaveDialog] minus the
 * AlertDialog scrim/window, so Robolectric tests can render it as a regular composable tree.
 */
@Composable
internal fun MeasureSaveDialogContent(
    onConfirm: (title: String?, note: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialTitle: String = "",
    initialNote: String = "",
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    val titleOverflow = title.length > NewMeasurement.MAX_TITLE_LENGTH
    val noteOverflow = note.length > NewMeasurement.MAX_NOTE_LENGTH
    val canSave = !titleOverflow && !noteOverflow

    Surface(
        modifier = modifier.testTag(MeasureSaveDialogTestTag),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(id = R.string.measure_save_dialog_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            MeasureSaveDialogFormBody(
                title = title,
                onTitleChange = { title = it },
                titleOverflow = titleOverflow,
                note = note,
                onNoteChange = { note = it },
                noteOverflow = noteOverflow,
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
                    modifier = Modifier.testTag(MeasureSaveDialogCancelTestTag),
                ) {
                    Text(stringResource(id = R.string.measure_save_cancel))
                }
                TextButton(
                    onClick = { onConfirm(title.ifEmpty { null }, note.ifEmpty { null }) },
                    enabled = canSave,
                    modifier = Modifier.testTag(MeasureSaveDialogConfirmTestTag),
                ) {
                    Text(stringResource(id = R.string.measure_save_confirm))
                }
            }
        }
    }
}

@Composable
private fun MeasureSaveDialogFormBody(
    title: String,
    onTitleChange: (String) -> Unit,
    titleOverflow: Boolean,
    note: String,
    onNoteChange: (String) -> Unit,
    noteOverflow: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(id = R.string.measure_save_title_label)) },
            placeholder = { Text(stringResource(id = R.string.measure_save_title_placeholder)) },
            singleLine = true,
            isError = titleOverflow,
            supportingText = {
                CounterText(
                    current = title.length,
                    limit = NewMeasurement.MAX_TITLE_LENGTH,
                    isError = titleOverflow,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MeasureSaveDialogTitleFieldTestTag),
        )
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            label = { Text(stringResource(id = R.string.measure_save_note_label)) },
            placeholder = { Text(stringResource(id = R.string.measure_save_note_placeholder)) },
            singleLine = false,
            minLines = 2,
            maxLines = 4,
            isError = noteOverflow,
            supportingText = {
                CounterText(
                    current = note.length,
                    limit = NewMeasurement.MAX_NOTE_LENGTH,
                    isError = noteOverflow,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MeasureSaveDialogNoteFieldTestTag),
        )
    }
}

@Composable
private fun CounterText(current: Int, limit: Int, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = "$current / $limit",
        color = color,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth(),
    )
}
