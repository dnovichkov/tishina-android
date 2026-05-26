package ru.dmdp.tishina.feature.history.ui

import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.feature.history.R

const val HistoryItemCardTestTagPrefix: String = "history_item_card_"
const val HistoryItemCardCheckmarkTestTagPrefix: String = "history_item_card_checkmark_"

/**
 * One row in the History list (FR-9 + FR-12).
 *
 * Layout: title + relative date + truncated note on the left, avg dB + min..max range
 * + sparkline mini-chart on the right. In selection mode an additional checkmark "well"
 * appears on the left edge — kept reserved (16dp + icon size) even for unselected cards
 * so the layout doesn't reflow as the user toggles items.
 *
 * Click semantics:
 *  - Normal mode: tap → [onClick] (Detail navigation), long-press → [onLongClick] (enter
 *    selection mode).
 *  - Selection mode: tap → [onClick] (toggle selection on this card), long-press → no-op.
 *
 * The two click handlers stay decoupled so the caller (HistoryScreen) controls policy:
 * the card never decides on its own whether tap means "navigate" or "toggle".
 *
 * Visuals:
 *  - selected card → `secondaryContainer` background (Material 3 multi-select pattern),
 *    visible CheckCircle icon, stateDescription "Selected" so TalkBack reads it without
 *    relying on color alone (NFR-15).
 *  - unselected-in-selection-mode card → same checkmark "well" reserved but no glyph
 *    drawn; container stays neutral.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    summary: MeasurementSummary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    val selectedStateDescription = stringResource(R.string.history_card_selected_cd)
    val longPressLabel = stringResource(R.string.history_card_long_press_cd)
    val cardModifier = modifier
        .fillMaxWidth()
        .testTag(HistoryItemCardTestTagPrefix + summary.id)
        .let { base ->
            if (onClick == null && onLongClick == null) {
                base
            } else {
                base.combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick?.let { lc -> { lc() } },
                    onLongClickLabel = longPressLabel.takeIf { onLongClick != null },
                )
            }
        }
        .semantics {
            if (selected) stateDescription = selectedStateDescription
        }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                SelectionCheckmark(itemId = summary.id, selected = selected)
                Spacer(modifier = Modifier.width(12.dp))
            }
            CardTextColumn(summary = summary, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            CardStatsColumn(summary = summary)
        }
    }
}

@Composable
private fun SelectionCheckmark(itemId: Long, selected: Boolean) {
    // Reserve the same square footprint whether selected or not — keeps the row layout
    // stable while the user toggles items.
    Box(
        modifier = Modifier.size(CheckmarkAreaSize),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(CheckmarkIconSize)
                    .testTag(HistoryItemCardCheckmarkTestTagPrefix + itemId),
            )
        }
    }
}

@Composable
private fun CardTextColumn(summary: MeasurementSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = summary.title?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.history_card_no_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = DateUtils.getRelativeTimeSpanString(
                summary.createdAtEpochMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL,
            ).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val noteText = summary.note
        if (!noteText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = noteText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.history_card_duration_cd, formatDuration(summary.durationMs)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CardStatsColumn(summary: MeasurementSummary) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = "%.1f".format(summary.avgDb),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "dB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "%.0f…%.0f".format(summary.minDb, summary.maxDb),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        SparklineChart(points = summary.sparklinePreview)
    }
}

private val CheckmarkAreaSize = 24.dp
private val CheckmarkIconSize = 24.dp

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val minutes = seconds / SECONDS_PER_MINUTE
    val remainder = seconds % SECONDS_PER_MINUTE
    return "%02d:%02d".format(minutes, remainder)
}
