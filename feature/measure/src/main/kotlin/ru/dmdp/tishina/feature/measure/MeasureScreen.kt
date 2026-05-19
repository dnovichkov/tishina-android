package ru.dmdp.tishina.feature.measure

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.dmdp.tishina.core.ui.components.PlaceholderScreen
import ru.dmdp.tishina.core.ui.R as CoreUiR

@Composable
fun MeasureScreen(
    onNavigateToAbout: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        titleRes = CoreUiR.string.measure_title,
        descriptionRes = CoreUiR.string.measure_placeholder,
        modifier = modifier,
    )
}
