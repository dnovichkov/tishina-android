package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.dmdp.tishina.feature.measure.R

const val AccuracyDisclaimerSheetTestTag: String = "accuracy_disclaimer_sheet"
const val AccuracyDisclaimerSheetTitleTestTag: String = "accuracy_disclaimer_sheet_title"
const val AccuracyDisclaimerSheetBodyTestTag: String = "accuracy_disclaimer_sheet_body"
const val AccuracyDisclaimerSheetMoreTestTag: String = "accuracy_disclaimer_sheet_more"

/**
 * Production-facing accuracy disclaimer sheet (FR-22).
 *
 * Mirrors the spec § 11 wording (MEMS microphones, ±3–5 dB(A), no IEC 61672 certification).
 * Hosted by [ru.dmdp.tishina.ui.TishinaApp] — clicking the «?» icon on the Measure tab opens
 * this sheet; «Learn more» dismisses and navigates to the full AboutScreen disclaimer card.
 *
 * Tests target [AccuracyDisclaimerSheetContent] directly: Material 3's [ModalBottomSheet]
 * renders in a separate Compose root with bottom-up settling animations that never reach
 * idle under Robolectric (`AppNotIdleException`). Same trade-off as [MeasureSaveDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccuracyDisclaimerBottomSheet(
    onDismiss: () -> Unit,
    onShowFullDisclaimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        modifier = modifier.testTag(AccuracyDisclaimerSheetTestTag),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        AccuracyDisclaimerSheetBody(onShowFullDisclaimer = onShowFullDisclaimer)
    }
}

/**
 * Window-less variant of the disclaimer sheet for Roborazzi + Compose UI tests. Same content
 * tree (title / body / «Learn more» row), wrapped in a [Surface] instead of a sheet Window.
 */
@Composable
internal fun AccuracyDisclaimerSheetContent(
    onShowFullDisclaimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(AccuracyDisclaimerSheetTestTag),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        AccuracyDisclaimerSheetBody(onShowFullDisclaimer = onShowFullDisclaimer)
    }
}

@Composable
private fun AccuracyDisclaimerSheetBody(onShowFullDisclaimer: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(id = R.string.measure_disclaimer_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(AccuracyDisclaimerSheetTitleTestTag),
        )
        Text(
            text = stringResource(id = R.string.measure_disclaimer_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(AccuracyDisclaimerSheetBodyTestTag),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onShowFullDisclaimer,
                modifier = Modifier.testTag(AccuracyDisclaimerSheetMoreTestTag),
            ) {
                Text(text = stringResource(id = R.string.measure_disclaimer_more))
            }
        }
    }
}
