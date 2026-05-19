package ru.dmdp.tishina.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Foundation-phase stub. Real palette, typography, shapes, and dynamic-color support
 * land in Task 4 of the foundation plan.
 */
@Composable
fun TishinaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(content = content)
}
