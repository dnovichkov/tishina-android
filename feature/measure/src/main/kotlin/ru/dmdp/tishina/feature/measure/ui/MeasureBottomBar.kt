package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.feature.measure.MeasurementPhase
import ru.dmdp.tishina.feature.measure.R

const val MeasureBottomBarTestTag: String = "measure_bottom_bar"
const val MeasureBottomBarStartPauseFabTestTag: String = "measure_bottom_bar_start_pause_fab"
const val MeasureBottomBarResetTestTag: String = "measure_bottom_bar_reset"
const val MeasureBottomBarSaveTestTag: String = "measure_bottom_bar_save"

/**
 * Bottom action bar with three primary controls: Reset (left), Start/Pause FAB (center),
 * Save (right).
 *
 * - Reset is only enabled in [MeasurementPhase.Paused] — resetting while idle is a no-op and
 *   resetting while running would silently drop captured data without confirmation.
 * - Save is gated by [saveEnabled] — callers compute "there is something worth saving" from the
 *   ViewModel state (any captured samples, or a Paused session that captured samples earlier).
 *   The button stays visible-but-disabled when nothing is buffered so the affordance is
 *   discoverable.
 * - The FAB swaps PlayArrow ↔ Pause based on phase; the click handler is the same — the
 *   ViewModel routes by phase via `StartRequested` / `PauseRequested`.
 */
@Composable
fun MeasureBottomBar(
    phase: MeasurementPhase,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveEnabled: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MeasureBottomBarTestTag),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onReset,
                enabled = phase == MeasurementPhase.Paused,
                modifier = Modifier.testTag(MeasureBottomBarResetTestTag),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(id = R.string.measure_action_reset),
                )
            }

            StartPauseFab(
                phase = phase,
                onClick = onStartPause,
            )

            IconButton(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.testTag(MeasureBottomBarSaveTestTag),
            ) {
                Icon(
                    imageVector = Icons.Outlined.BookmarkBorder,
                    contentDescription = stringResource(
                        id = if (saveEnabled) R.string.measure_action_save else R.string.measure_save_disabled_cd,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StartPauseFab(
    phase: MeasurementPhase,
    onClick: () -> Unit,
) {
    val (icon, descRes) = when (phase) {
        MeasurementPhase.Running -> Icons.Filled.Pause to R.string.measure_action_pause
        MeasurementPhase.Idle, MeasurementPhase.Paused ->
            Icons.Filled.PlayArrow to R.string.measure_action_start
    }
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.testTag(MeasureBottomBarStartPauseFabTestTag),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(),
    ) {
        Icon(imageVector = icon, contentDescription = stringResource(id = descRes))
    }
}
