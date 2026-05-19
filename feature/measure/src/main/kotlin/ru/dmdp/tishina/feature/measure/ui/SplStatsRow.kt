package ru.dmdp.tishina.feature.measure.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.feature.measure.R
import java.util.Locale

const val SplStatsRowTestTag: String = "measure_spl_stats_row"
const val SplStatsMinTestTag: String = "measure_spl_stats_min"
const val SplStatsAvgTestTag: String = "measure_spl_stats_avg"
const val SplStatsMaxTestTag: String = "measure_spl_stats_max"

/**
 * Min / Avg / Max strip below the readout. Filled Material 3 cards make each cell visually
 * distinct from the surrounding readout/gauge area without requiring divider lines.
 *
 * Sentinel values ([Float.POSITIVE_INFINITY] / [Float.NEGATIVE_INFINITY]) render as "—" rather
 * than a misleading "+Infinity" — these are the initial state before the first sample arrives.
 */
@Composable
fun SplStatsRow(
    min: Float,
    avg: Float,
    max: Float,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .testTag(SplStatsRowTestTag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCell(
            labelRes = R.string.measure_stats_min,
            value = min,
            tag = SplStatsMinTestTag,
            modifier = Modifier.weight(1f),
        )
        StatCell(
            labelRes = R.string.measure_stats_avg,
            value = avg,
            tag = SplStatsAvgTestTag,
            modifier = Modifier.weight(1f),
        )
        StatCell(
            labelRes = R.string.measure_stats_max,
            value = max,
            tag = SplStatsMaxTestTag,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCell(
    @StringRes labelRes: Int,
    value: Float,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag(tag),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(id = labelRes),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (value.isFinite()) {
                    String.format(Locale.ROOT, "%.1f", value)
                } else {
                    stringResource(id = R.string.measure_stats_placeholder)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
