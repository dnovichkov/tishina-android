package ru.dmdp.tishina.core.ui.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val PreferenceCategoryTestTag: String = "tishina_preference_category"
const val PreferenceCategoryTitleTestTag: String = "tishina_preference_category_title"

/**
 * Settings-screen section header + slot for preference rows.
 *
 * Implemented as a stateless [Column] rather than a custom layout so callers can
 * compose any vertical list of preferences (rows, sliders, switches) inside it
 * — including conditional rows like the API-gated "Dynamic colors" tile from
 * Task 6.
 */
@Composable
fun PreferenceCategory(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(contentPadding)
            .testTag(PreferenceCategoryTestTag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .testTag(PreferenceCategoryTitleTestTag),
        )
        content()
    }
}
