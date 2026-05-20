package ru.dmdp.tishina.feature.history

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.dmdp.tishina.feature.history.ui.HistoryEmptyState
import ru.dmdp.tishina.feature.history.ui.HistoryErrorState
import ru.dmdp.tishina.feature.history.ui.HistoryItemCard

const val HistoryScreenTestTag: String = "history_screen"
const val HistoryListTestTag: String = "history_list"
const val HistorySwipeBackgroundTestTagPrefix: String = "history_swipe_bg_"

/**
 * Main History entry point (FR-8…FR-11).
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

    LaunchedEffect(viewModel) {
        // Only error snackbars go through the effect channel — Undo lives in state
        // (see HistoryScreenContent below). One-shot effects are appropriate for ephemeral
        // notifications that don't need to survive recomposition; the Undo affordance does,
        // so it's bound to the VM's pendingUndoId state instead.
        viewModel.effects.collect { effect ->
            when (effect) {
                is HistoryUiEffect.ShowErrorSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = context.getString(effect.messageRes),
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
    val context = LocalContext.current
    // Undo snackbar is driven by `state.pendingUndoId`, NOT by a one-shot effect.
    //
    // Rationale (rotation): a one-shot effect is consumed by the previous Composition;
    // after rotation or theme-change the recreated screen never sees it and the user
    // loses the Undo affordance while the VM's commit timer keeps running. Driving
    // from state means a mid-window recomposition re-attaches the snackbar with the
    // same id, and when the VM clears `pendingUndoId` (commit or undo) the
    // LaunchedEffect re-keys, cancelling `showSnackbar` and dismissing the snackbar
    // at the exact moment of the commit.
    //
    // Rationale (queued snackbar): we explicitly dismiss any currently-visible snackbar
    // before queuing the Undo. Otherwise the Undo would queue behind an in-flight error
    // toast and the visible Undo window would compress (or vanish entirely), while the
    // VM's independent 5 s commit timer still fires.
    val pendingUndoId = state.pendingUndoId
    LaunchedEffect(pendingUndoId) {
        if (pendingUndoId == null) return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.history_undo_snackbar_message),
            actionLabel = context.getString(R.string.history_undo_action),
            // Indefinite — the VM owns the lifetime; we dismiss on `pendingUndoId` clear.
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onEvent(HistoryUiEvent.UndoConfirmed)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(HistoryScreenTestTag),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .testTag(HistoryListTestTag),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
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
                            // Single-direction swipe: left → delete; right does nothing
                            // (no archive / bulk-move flow in MVP, plan line 32).
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = true,
                            backgroundContent = { SwipeDeleteBackground(item.id) },
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            HistoryItemCard(
                                summary = item,
                                onClick = { onNavigateToDetail(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
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
