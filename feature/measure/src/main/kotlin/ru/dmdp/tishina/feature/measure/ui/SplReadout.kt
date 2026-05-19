package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.dmdp.tishina.core.designsystem.theme.LocalSplLevelPalette
import ru.dmdp.tishina.core.designsystem.theme.levelToSplColor
import ru.dmdp.tishina.feature.measure.R
import java.util.Locale

const val SplReadoutTestTag: String = "measure_spl_readout"
const val SplReadoutValueTestTag: String = "measure_spl_readout_value"
const val SplReadoutUnitTestTag: String = "measure_spl_readout_unit"

/**
 * Live dB readout — the centerpiece of the Measure screen.
 *
 * Renders the dB value as a 96 sp tabular numeral (digits do not shift horizontally as they
 * cycle, otherwise the screen would jitter at 10 Hz refresh) next to a small `dB A` suffix.
 * The whole row carries a single combined contentDescription so TalkBack reads "63 decibels
 * A-weighted" as one phrase instead of stepping through value + unit.
 */
@Composable
fun SplReadout(
    db: Float,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    val palette = LocalSplLevelPalette.current
    val color = levelToSplColor(db, palette)
    val formatted = if (db.isFinite()) String.format(Locale.ROOT, "%.1f", db) else "—"
    val accessibilityLabel = stringResource(id = R.string.measure_readout_cd, db)

    Row(
        modifier = modifier
            .padding(contentPadding)
            .testTag(SplReadoutTestTag)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
            },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = formatted,
            color = color,
            fontSize = 96.sp,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFeatureSettings = "tnum",
            ),
            modifier = Modifier.testTag(SplReadoutValueTestTag),
        )
        Text(
            text = stringResource(id = R.string.measure_unit_db_a),
            color = LocalContentColor.current,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .testTag(SplReadoutUnitTestTag),
        )
    }
}
