package ru.dmdp.tishina.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.dmdp.tishina.core.ui.components.PlaceholderScreen
import ru.dmdp.tishina.core.ui.R as CoreUiR

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        titleRes = CoreUiR.string.settings_title,
        descriptionRes = CoreUiR.string.settings_placeholder,
        modifier = modifier,
    )
}
