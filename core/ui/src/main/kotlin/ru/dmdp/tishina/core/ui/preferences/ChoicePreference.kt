package ru.dmdp.tishina.core.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

const val ChoicePreferenceTestTag: String = "tishina_choice_preference"
const val ChoicePreferenceChipTestTagPrefix: String = "tishina_choice_preference_chip_"

/**
 * Row of filter chips driven by a generic [selectedOption]. Used for theme,
 * locale and time-weighting selectors.
 *
 * `FilterChip` (rather than `SegmentedButtonRow`) gives stable API surface and
 * built-in wrapping via [FlowRow] — important on 360 dp screens where three
 * Russian labels ("Системный", "Русский", "English") would otherwise truncate.
 *
 * @param options canonical values returned by [onOptionSelected]
 * @param optionLabels human-readable labels — index must match [options]
 * @param contentDescriptions optional per-option `contentDescription` for TalkBack;
 *   if null we fall back to the visible label, which is sufficient for plain text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoicePreference(
    title: String,
    options: List<T>,
    optionLabels: List<String>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentDescriptions: List<String>? = null,
) {
    require(options.size == optionLabels.size) {
        "options and optionLabels must have the same length"
    }
    require(contentDescriptions == null || contentDescriptions.size == options.size) {
        "contentDescriptions length must match options when provided"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ChoicePreferenceTestTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, option ->
                val label = optionLabels[index]
                val cd = contentDescriptions?.get(index)
                FilterChip(
                    selected = option == selectedOption,
                    onClick = { onOptionSelected(option) },
                    label = { Text(text = label) },
                    modifier = Modifier
                        .testTag("$ChoicePreferenceChipTestTagPrefix$index")
                        .then(
                            if (cd != null) {
                                Modifier.semantics { contentDescription = cd }
                            } else {
                                Modifier
                            },
                        ),
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
    }
}
