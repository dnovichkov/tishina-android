package ru.dmdp.tishina.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.dmdp.tishina.core.ui.components.PlaceholderScreen
import ru.dmdp.tishina.core.ui.R as CoreUiR

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        titleRes = CoreUiR.string.history_title,
        descriptionRes = CoreUiR.string.history_placeholder,
        modifier = modifier,
    )
}
