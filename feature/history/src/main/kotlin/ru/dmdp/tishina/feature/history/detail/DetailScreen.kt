package ru.dmdp.tishina.feature.history.detail

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.ui.components.chart.SplLineChart
import ru.dmdp.tishina.core.ui.components.chart.SplStatsRow
import ru.dmdp.tishina.feature.history.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val DetailScreenTestTag: String = "detail_screen"
const val DetailNoteCardTestTag: String = "detail_note_card"
const val DetailNoteFieldTestTag: String = "detail_note_field"
const val DetailNoteSaveTestTag: String = "detail_note_save"
const val DetailNoteCancelTestTag: String = "detail_note_cancel"
const val DetailDeleteIconTestTag: String = "detail_delete_icon"
const val DetailDeleteDialogTestTag: String = "detail_delete_dialog"
const val DetailBackIconTestTag: String = "detail_back_icon"

private const val DURATION_MS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

/**
 * Public Hilt-aware entry point. Splits into [DetailScreenContent] so the inner composable
 * is trivially testable via `createComposeRule()` without a Hilt graph.
 */
@Composable
fun DetailScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DetailUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(context.getString(effect.messageRes))
                }
                DetailUiEffect.NavigateBack -> onNavigateBack()
            }
        }
    }

    DetailScreenContent(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailScreenContent(
    state: DetailUiState,
    onEvent: (DetailUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(DetailScreenTestTag),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = state.details?.summary?.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.detail_note_default_title),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag(DetailBackIconTestTag),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back_cd),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(DetailUiEvent.DeleteRequested) },
                        modifier = Modifier.testTag(DetailDeleteIconTestTag),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.detail_delete_cd),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val details = state.details
        if (details == null) {
            // While loading or after a redirect — leave a quiet empty area, no spinner.
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 8.dp, vertical = 8.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SplLineChart(samples = details.samples, height = 160.dp)
            SplStatsRow(min = details.summary.minDb, avg = details.summary.avgDb, max = details.summary.maxDb)
            MetadataCard(details = details)
            NoteCard(
                editing = state.editingNote,
                draft = state.noteDraft,
                onStartEditing = { onEvent(DetailUiEvent.StartEditingNote) },
                onChange = { onEvent(DetailUiEvent.NoteChanged(it)) },
                onSave = { onEvent(DetailUiEvent.SaveNote) },
                onCancel = { onEvent(DetailUiEvent.CancelEditingNote) },
            )
        }
        if (state.deleteConfirmVisible) {
            DeleteConfirmDialog(
                onConfirm = { onEvent(DetailUiEvent.DeleteConfirmed) },
                onCancel = { onEvent(DetailUiEvent.DeleteCancelled) },
            )
        }
    }
}

@Composable
private fun MetadataCard(details: MeasurementDetails) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetadataRow(
                label = stringResource(R.string.detail_meta_created_at),
                value = formatCreatedAt(details.summary.createdAtEpochMs),
            )
            MetadataRow(
                label = stringResource(R.string.detail_meta_duration),
                value = formatDuration(details.summary.durationMs),
            )
            MetadataRow(
                label = stringResource(R.string.detail_meta_weighting),
                value = weightingLabel(details.weighting),
            )
            MetadataRow(
                label = stringResource(R.string.detail_meta_time_weighting),
                value = timeWeightingLabel(details.timeWeighting),
            )
            MetadataRow(
                label = stringResource(R.string.detail_meta_offset),
                value = stringResource(R.string.detail_meta_offset_value, details.calibrationOffsetDb),
            )
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NoteCard(
    editing: Boolean,
    draft: String,
    onStartEditing: () -> Unit,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DetailNoteCardTestTag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.detail_note_card_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editing) {
                NoteEditor(
                    draft = draft,
                    onChange = onChange,
                    onSave = onSave,
                    onCancel = onCancel,
                )
            } else {
                NoteReadOnly(draft = draft, onClick = onStartEditing)
            }
        }
    }
}

@Composable
private fun NoteReadOnly(draft: String, onClick: () -> Unit) {
    val displayText = draft.takeIf { it.isNotBlank() } ?: stringResource(R.string.detail_add_note)
    val color = if (draft.isBlank()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = displayText,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onClick) {
            Text(text = stringResource(R.string.detail_note_label))
        }
    }
}

@Composable
private fun NoteEditor(
    draft: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val overflow = draft.length > NewMeasurement.MAX_NOTE_LENGTH
    OutlinedTextField(
        value = draft,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DetailNoteFieldTestTag),
        isError = overflow,
        minLines = 2,
        maxLines = 6,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        supportingText = {
            Text(
                text = stringResource(
                    R.string.detail_note_counter,
                    draft.length,
                    NewMeasurement.MAX_NOTE_LENGTH,
                ),
                color = if (overflow) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.testTag(DetailNoteCancelTestTag),
        ) {
            Text(text = stringResource(R.string.detail_note_cancel))
        }
        TextButton(
            onClick = onSave,
            enabled = !overflow,
            modifier = Modifier.testTag(DetailNoteSaveTestTag),
        ) {
            Text(text = stringResource(R.string.detail_note_save))
        }
    }
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag(DetailDeleteDialogTestTag),
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.detail_delete_confirm_title)) },
        text = { Text(text = stringResource(R.string.detail_delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.detail_delete_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.detail_delete_cancel_action))
            }
        },
    )
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / DURATION_MS_PER_SECOND).coerceAtLeast(0L)
    val minutes = seconds / SECONDS_PER_MINUTE
    val remainder = seconds % SECONDS_PER_MINUTE
    return "%02d:%02d".format(minutes, remainder)
}

@Composable
private fun formatCreatedAt(epochMs: Long): String {
    val context = LocalContext.current
    // Honor the user's 12/24-hour preference for time-of-day; date follows the locale.
    val is24h = DateFormat.is24HourFormat(context)
    val pattern = if (is24h) "d MMM yyyy, HH:mm" else "d MMM yyyy, h:mm a"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochMs))
}

private fun weightingLabel(weighting: FrequencyWeighting): String = when (weighting) {
    FrequencyWeighting.A -> "A"
    FrequencyWeighting.Z -> "Z"
}

private fun timeWeightingLabel(timeWeighting: TimeWeighting): String = when (timeWeighting) {
    TimeWeighting.FAST -> "Fast"
    TimeWeighting.SLOW -> "Slow"
}
