package ru.dmdp.tishina.feature.measure

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import ru.dmdp.tishina.feature.measure.ui.MeasureBottomBar
import ru.dmdp.tishina.feature.measure.ui.PermissionRationaleDialog
import ru.dmdp.tishina.feature.measure.ui.SplArcGauge
import ru.dmdp.tishina.feature.measure.ui.SplLineChart
import ru.dmdp.tishina.feature.measure.ui.SplReadout
import ru.dmdp.tishina.feature.measure.ui.SplStatsRow

const val MeasureScreenTestTag: String = "measure_screen"
const val MeasureScreenDurationTestTag: String = "measure_screen_duration"

/**
 * Measure screen — main entry point for live SPL monitoring.
 *
 * Architecture: [MeasureScreen] is a thin wrapper that wires Hilt + permission scaffolding
 * onto the testable [MeasureScreenContent] composable. Splitting them lets the screenshot
 * tests render the UI with a hand-built state without needing the Activity host or a real
 * `ActivityResultLauncher`.
 *
 * Permission handling:
 *  - `RequestPermission` effect → launches the system permission dialog;
 *  - `OpenAppSettings` effect → routes to system settings for permanent denial recovery;
 *  - `ShowSnackbar` effect → surfaces transient feedback (denial, save unavailable).
 *
 * The screen does not own a TopAppBar — the app-level [TishinaApp] hosts a shared
 * `CenterAlignedTopAppBar` for every nav destination, so adding another bar here would
 * stack them.
 */
@Composable
fun MeasureScreen(
    modifier: Modifier = Modifier,
    viewModel: MeasureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // After a denial, `shouldShowRationale` tells us whether the user can still be
        // re-prompted (true) or has selected "Don't ask again" (false → must route to
        // system settings).
        val activity = context as? Activity
        val rationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
        } == true
        viewModel.onEvent(
            MeasureUiEvent.PermissionResult(granted = granted, shouldShowRationale = rationale),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MeasureUiEffect.RequestPermission -> {
                    if (state.permissionState == PermissionState.Denied) {
                        showRationale = true
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                is MeasureUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(context.getString(effect.messageRes))
                }
                is MeasureUiEffect.OpenAppSettings -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    MeasureScreenContent(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        showRationale = showRationale,
        onRationaleConfirm = {
            showRationale = false
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onRationaleDismiss = { showRationale = false },
        modifier = modifier,
    )
}

@Composable
internal fun MeasureScreenContent(
    state: MeasureUiState,
    onEvent: (MeasureUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    showRationale: Boolean,
    onRationaleConfirm: () -> Unit,
    onRationaleDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(MeasureScreenTestTag),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            MeasureBottomBar(
                phase = state.phase,
                onStartPause = {
                    if (state.phase == MeasurementPhase.Running) {
                        onEvent(MeasureUiEvent.PauseRequested)
                    } else {
                        onEvent(MeasureUiEvent.StartRequested)
                    }
                },
                onReset = { onEvent(MeasureUiEvent.ResetRequested) },
                onSave = { onEvent(MeasureUiEvent.SaveRequested) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 8.dp, vertical = 8.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SplReadout(db = state.current)
            SplArcGauge(db = state.current)
            SplStatsRow(min = state.min, avg = state.avg, max = state.max)
            SplLineChart(samples = state.recent)
            Text(
                text = stringResource(
                    id = R.string.measure_duration_label,
                    formatDuration(state.durationMs),
                ),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag(MeasureScreenDurationTestTag),
            )
        }

        if (showRationale) {
            PermissionRationaleDialog(
                onConfirm = onRationaleConfirm,
                onDismiss = onRationaleDismiss,
            )
        }
    }
}

private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val minutes = seconds / SECONDS_PER_MINUTE
    val remainder = seconds % SECONDS_PER_MINUTE
    return "%02d:%02d".format(minutes, remainder)
}
