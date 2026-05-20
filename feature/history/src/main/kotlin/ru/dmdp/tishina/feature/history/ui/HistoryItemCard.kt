package ru.dmdp.tishina.feature.history.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.feature.history.R

const val HistoryItemCardTestTagPrefix: String = "history_item_card_"

/**
 * One row in the History list (FR-9).
 *
 * Layout: title + relative date + truncated note on the left, avg dB + min..max range
 * + sparkline mini-chart on the right. Optional `onClick` drives the navigation to the
 * Detail screen — passing `null` keeps the card non-interactive (used in screenshot tests).
 *
 * Title fallback: when [MeasurementSummary.title] is null/blank we show the localized
 * "Замер" / "Measurement" string so the row never looks broken — the spec § 6 makes the
 * date primary and title secondary.
 */
@Composable
fun HistoryItemCard(
    summary: MeasurementSummary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .testTag(HistoryItemCardTestTagPrefix + summary.id)
        .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardTextColumn(summary = summary, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            CardStatsColumn(summary = summary)
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

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val minutes = seconds / SECONDS_PER_MINUTE
    val remainder = seconds % SECONDS_PER_MINUTE
    return "%02d:%02d".format(minutes, remainder)
}
