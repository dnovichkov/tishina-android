package ru.dmdp.tishina.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.dmdp.tishina.core.ui.components.PlaceholderScreen
import ru.dmdp.tishina.core.ui.R as CoreUiR

@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        titleRes = CoreUiR.string.about_title,
        descriptionRes = CoreUiR.string.about_placeholder,
        modifier = modifier,
    )
}
