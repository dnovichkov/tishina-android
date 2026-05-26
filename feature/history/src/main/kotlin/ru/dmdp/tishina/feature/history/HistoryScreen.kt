package ru.dmdp.tishina.feature.history

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.dmdp.tishina.core.domain.model.ExportFilter
import ru.dmdp.tishina.feature.history.ui.BulkDeleteConfirmDialog
import ru.dmdp.tishina.feature.history.ui.HistoryEmptyState
import ru.dmdp.tishina.feature.history.ui.HistoryErrorState
import ru.dmdp.tishina.feature.history.ui.HistoryItemCard
import ru.dmdp.tishina.feature.history.ui.HistorySelectionTopBar

const val HistoryScreenTestTag: String = "history_screen"
const val HistoryListTestTag: String = "history_list"
const val HistorySwipeBackgroundTestTagPrefix: String = "history_swipe_bg_"
const val HistoryExportButtonTestTag: String = "history_export_button"

/**
 * Main History entry point (FR-8…FR-12).
 *
 * Architecture mirrors [ru.dmdp.tishina.feature.measure.MeasureScreen]: a thin Hilt-aware
 * wrapper that wires the ViewModel + permission-free side effects onto [HistoryScreenContent].
 * Splitting them keeps the inner composable trivially testable from `createComposeRule()`
 * without needing a Hilt graph.
 *
 * Navigation callbacks are required at the call-site (no defaults) so the parent NavGraph
 * cannot accidentally render a History screen with stub routing — type-safety > convenience.
 */
@Composable
fun HistoryScreen(
    onNavigateToDetail: (Long) -> Unit = {},
    onNavigateToMeasure: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // SAF CreateDocument launcher (FR-20). Activity result is consumed off the system picker and
    // forwarded into the ViewModel as ExportFileSelected (URI present) or ExportCancelled (null).
    //
    // We keep `pendingExportFilter` here in the screen layer because the SAF picker callback runs
    // on the main Activity result thread without our event payload — we need a side channel that
    // survives the round-trip. Reset to null on every new ExportRequested effect so a previously
    // unfinished picker can never silently leak into the next export.
    var pendingExportFilter: ExportFilter? by remember { mutableStateOf(null) }
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        val filter = pendingExportFilter ?: return@rememberLauncherForActivityResult
        pendingExportFilter = null
        if (uri == null) {
            viewModel.onEvent(HistoryUiEvent.ExportCancelled)
        } else {
            viewModel.onEvent(HistoryUiEvent.ExportFileSelected(uri.toString(), filter))
        }
    }

    LaunchedEffect(viewModel) {
        // Only error snackbars + the SAF picker / export snackbars go through the effect
        // channel — Undo (single + bulk) lives in state. One-shot effects are appropriate for
        // ephemeral notifications that don't need to survive recomposition; Undo affordances do,
        // so they're bound to VM state instead.
        viewModel.effects.collect { effect ->
            when (effect) {
                is HistoryUiEffect.ShowErrorSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(effect.messageRes),
                        duration = SnackbarDuration.Short,
                    )
                }
                is HistoryUiEffect.LaunchSafPicker -> {
                    pendingExportFilter = effect.filter
                    csvPickerLauncher.launch(effect.suggestedName)
                }
                is HistoryUiEffect.ShowExportSuccessSnackbar -> {
                    val message = context.resources.getQuantityString(
                        R.plurals.history_export_success,
                        effect.rowCount,
                        effect.rowCount,
                    )
                    snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
                }
                HistoryUiEffect.ShowExportFailedSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(R.string.history_export_failed),
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        }
    }

    HistoryScreenContent(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        onNavigateToDetail = onNavigateToDetail,
        onNavigateToMeasure = onNavigateToMeasure,
        modifier = modifier,
    )
}

@Composable
internal fun HistoryScreenContent(
    state: HistoryUiState,
    onEvent: (HistoryUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToMeasure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleUndoSnackbarBinder(
        pendingUndoId = state.pendingUndoId,
        snackbarHostState = snackbarHostState,
        onUndoConfirmed = { onEvent(HistoryUiEvent.UndoConfirmed) },
    )
    BulkUndoSnackbarBinder(
        pendingBulkCount = state.pendingBulkUndoCount,
        snackbarHostState = snackbarHostState,
        onBulkUndoConfirmed = { onEvent(HistoryUiEvent.BulkUndoConfirmed) },
    )

    // Selection-mode hardware Back: instead of unwinding the screen (which would dump the
    // user back into Measure) we exit selection mode in-place.
    BackHandler(enabled = state.selectionMode) {
        onEvent(HistoryUiEvent.ExitSelectionMode)
    }

    // Local UI-only state — the confirm dialog visibility is a presentation concern, not VM.
    // We open it on a Delete tap and emit BulkDeleteRequested only after the user confirms;
    // the VM never sees an intermediate "Delete pressed but not confirmed" state.
    // `rememberSaveable` so a config change (rotation, theme/locale flip) keeps the dialog
    // visible — mirrors the `showDisclaimerSheet` pattern in `TishinaApp`.
    var showBulkConfirm by rememberSaveable { mutableStateOf(false) }

    // Auto-dismiss the dialog if the underlying selection becomes empty or selection mode is
    // exited from elsewhere (BulkUndoConfirmed, async error path, programmatic ExitSelectionMode).
    // Without this guard the dialog could survive a state change and show a stale count, then
    // confirming would either hit `scheduleBulkDelete`'s "empty selection" error branch or
    // delete a different count than the dialog advertised.
    LaunchedEffect(state.selectionMode, state.selectedIds.isEmpty()) {
        if (!state.selectionMode || state.selectedIds.isEmpty()) {
            showBulkConfirm = false
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(HistoryScreenTestTag),
        topBar = {
            when {
                state.selectionMode -> HistorySelectionTopBar(
                    selectedCount = state.selectedIds.size,
                    totalCount = state.items.size,
                    onSelectAll = { onEvent(HistoryUiEvent.SelectAll) },
                    onClearSelection = { onEvent(HistoryUiEvent.ClearSelection) },
                    onCancel = { onEvent(HistoryUiEvent.ExitSelectionMode) },
                    onDelete = { showBulkConfirm = true },
                )
                state.items.isNotEmpty() -> HistoryDefaultTopBar(
                    onExportAll = { onEvent(HistoryUiEvent.ExportRequested(ExportFilter.All)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        HistoryScreenBody(
            state = state,
            onEvent = onEvent,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToMeasure = onNavigateToMeasure,
            padding = padding,
        )
    }

    // Render guarded on a non-empty selection — combined with the LaunchedEffect above this
    // makes the "Delete N" count truthful: we never display a stale or fallback "1" count.
    if (showBulkConfirm && state.selectedIds.isNotEmpty()) {
        BulkDeleteConfirmDialog(
            count = state.selectedIds.size,
            onConfirm = {
                showBulkConfirm = false
                onEvent(HistoryUiEvent.BulkDeleteRequested)
            },
            onDismiss = { showBulkConfirm = false },
        )
    }
}

/**
 * Single-delete Undo snackbar — driven by `pendingUndoId`, NOT a one-shot effect.
 *
 * Rationale (rotation): a one-shot effect is consumed by the previous Composition;
 * after rotation or theme-change the recreated screen never sees it and the user
 * loses the Undo affordance while the VM's commit timer keeps running. Driving from
 * state means a recomposition re-attaches the snackbar, and when the VM clears
 * `pendingUndoId` the LaunchedEffect re-keys and dismisses the snackbar at the exact
 * commit moment.
 *
 * Rationale (queued snackbar): we explicitly dismiss any currently-visible snackbar
 * before queuing the Undo so it never gets stuck behind an in-flight error toast.
 */
@Composable
private fun SingleUndoSnackbarBinder(
    pendingUndoId: Long?,
    snackbarHostState: SnackbarHostState,
    onUndoConfirmed: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(pendingUndoId) {
        if (pendingUndoId == null) return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.history_undo_snackbar_message),
            actionLabel = context.getString(R.string.history_undo_action),
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndoConfirmed()
        }
    }
}

/**
 * Bulk-delete Undo snackbar — same state-driven contract as the single variant, with a
 * pluralized message resolved from `pendingBulkCount`. The two snackbars are mutually
 * exclusive in the VM (each new soft-delete kind orphan-commits the other), so we never
 * queue both simultaneously.
 */
@Composable
private fun BulkUndoSnackbarBinder(
    pendingBulkCount: Int,
    snackbarHostState: SnackbarHostState,
    onBulkUndoConfirmed: () -> Unit,
) {
    val context = LocalContext.current
    val message = if (pendingBulkCount > 0) {
        pluralStringResource(R.plurals.history_bulk_undo_message, pendingBulkCount, pendingBulkCount)
    } else {
        null
    }
    LaunchedEffect(pendingBulkCount, message) {
        if (pendingBulkCount <= 0 || message == null) return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = context.getString(R.string.history_bulk_undo_action),
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onBulkUndoConfirmed()
        }
    }
}

@Composable
private fun HistoryScreenBody(
    state: HistoryUiState,
    onEvent: (HistoryUiEvent) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToMeasure: () -> Unit,
    padding: PaddingValues,
) {
    when {
        state.loading -> {
            // Render nothing while loading — Phase 3 keeps it quiet rather than flashing a
            // spinner; the initial Room emission lands fast enough that a spinner would
            // typically flicker for one frame and disappear. NFR-1 (cold start ≤ 1 s) is
            // best served by *not* mounting a transient progress indicator.
            Box(modifier = Modifier.fillMaxSize().padding(padding))
        }
        state.loadFailed -> {
            // Distinct from the empty-state branch: an error means the data might still be
            // on disk but unreadable — the CTA "make first measurement" would be misleading.
            HistoryErrorState(modifier = Modifier.padding(padding))
        }
        state.items.isEmpty() -> {
            HistoryEmptyState(
                onNavigateToMeasure = onNavigateToMeasure,
                modifier = Modifier.padding(padding),
            )
        }
        else -> {
            HistoryList(
                state = state,
                onEvent = onEvent,
                onNavigateToDetail = onNavigateToDetail,
                padding = padding,
            )
        }
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    onEvent: (HistoryUiEvent) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag(HistoryListTestTag),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        items(state.items, key = { it.id }) { item ->
            val cardSelected = item.id in state.selectedIds
            // Swipe-to-delete is disabled in selection mode — gesture conflict + the user
            // already has bulk-delete affordance via the action bar.
            if (state.selectionMode) {
                HistoryItemCard(
                    summary = item,
                    selectionMode = true,
                    selected = cardSelected,
                    onClick = { onEvent(HistoryUiEvent.ToggleSelection(item.id)) },
                    onLongClick = null,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            onEvent(HistoryUiEvent.DeleteRequested(item.id))
                            true
                        } else {
                            false
                        }
                    },
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    enableDismissFromEndToStart = true,
                    backgroundContent = { SwipeDeleteBackground(item.id) },
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    HistoryItemCard(
                        summary = item,
                        selectionMode = false,
                        selected = false,
                        onClick = { onNavigateToDetail(item.id) },
                        onLongClick = { onEvent(HistoryUiEvent.EnterSelectionMode(item.id)) },
                    )
                }
            }
        }
    }
}

/**
 * Default top app bar visible when there is at least one measurement and the screen is NOT in
 * selection mode. Hosts only the "Export all to CSV" action (FR-20). Keeping the title empty
 * matches the existing minimalist look of the History screen — we don't waste vertical real
 * estate on a redundant "History" header (the bottom-nav already identifies the destination).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDefaultTopBar(onExportAll: () -> Unit) {
    TopAppBar(
        title = { Text("") },
        actions = {
            IconButton(
                onClick = onExportAll,
                modifier = Modifier.testTag(HistoryExportButtonTestTag),
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileDownload,
                    contentDescription = stringResource(R.string.history_export_all_cd),
                )
            }
        },
    )
}

@Composable
private fun SwipeDeleteBackground(itemId: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag(HistorySwipeBackgroundTestTagPrefix + itemId),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(R.string.history_card_delete_cd),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
