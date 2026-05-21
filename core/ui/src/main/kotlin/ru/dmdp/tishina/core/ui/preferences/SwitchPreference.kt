package ru.dmdp.tishina.core.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

const val SwitchPreferenceTestTag: String = "tishina_switch_preference"
const val SwitchPreferenceSwitchTestTag: String = "tishina_switch_preference_switch"

/**
 * Row preference with a Material 3 [Switch] on the trailing edge.
 *
 * The entire row is `toggleable(role = Switch)` so TalkBack reads the full
 * label + state and one tap anywhere flips the value — standard Material
 * pattern. When [enabled] is false the row neither emits callbacks nor reads
 * as toggleable, used for the Android-12+ gating of "Dynamic colors".
 */
@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Switch,
            )
            .padding(vertical = 8.dp)
            .testTag(SwitchPreferenceTestTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            // The row's toggleable already routes clicks; setting null here keeps the
            // switch visually live but prevents the inner Switch from double-firing.
            onCheckedChange = null,
            enabled = enabled,
            modifier = Modifier.testTag(SwitchPreferenceSwitchTestTag),
        )
    }
}
