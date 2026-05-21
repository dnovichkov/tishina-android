package ru.dmdp.tishina.core.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

const val SliderPreferenceTestTag: String = "tishina_slider_preference"
const val SliderPreferenceSliderTestTag: String = "tishina_slider_preference_slider"
const val SliderPreferenceValueTestTag: String = "tishina_slider_preference_value"
const val SliderPreferenceResetTestTag: String = "tishina_slider_preference_reset"

/**
 * Calibration-style slider preference with a current-value label and an
 * optional reset button below.
 *
 * Drag UX: local state holds the in-flight thumb position so the user sees
 * smooth motion, and we only push the committed value upstream from
 * `onValueChangeFinished`. Without this debounce-by-design every DataStore
 * write during a drag would trigger a re-emission, which Compose handles fine
 * but burns IO unnecessarily.
 *
 * @param valueContentDescription optional accessibility text builder driven by
 *   the *committed* value — required for FR / NFR-13 since the visible label
 *   alone reads as a number without context.
 */
@Composable
fun SliderPreference(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueFormatter: (Float) -> String,
    modifier: Modifier = Modifier,
    onResetClick: (() -> Unit)? = null,
    resetButtonLabel: String? = null,
    description: String? = null,
    valueContentDescription: ((Float) -> String)? = null,
) {
    var localValue by remember(value) { mutableFloatStateOf(value) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SliderPreferenceTestTag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(SliderPreferenceValueTestTag),
            )
        }
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChange(localValue) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SliderPreferenceSliderTestTag)
                .then(
                    if (valueContentDescription != null) {
                        Modifier.semantics { contentDescription = valueContentDescription(value) }
                    } else {
                        Modifier
                    },
                ),
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onResetClick != null && resetButtonLabel != null) {
            FilledTonalButton(
                onClick = onResetClick,
                modifier = Modifier.testTag(SliderPreferenceResetTestTag),
            ) {
                Text(text = resetButtonLabel)
            }
        }
    }
}
