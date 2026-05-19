package ru.dmdp.tishina.core.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val AppTopBarTestTag: String = "tishina_app_top_bar"
const val AppTopBarAboutActionTestTag: String = "tishina_app_top_bar_about"
const val AppTopBarSettingsActionTestTag: String = "tishina_app_top_bar_settings"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onAboutClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    aboutContentDescription: String? = null,
    settingsContentDescription: String? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    LargeTopAppBar(
        title = { Text(text = title) },
        modifier = modifier.testTag(AppTopBarTestTag),
        windowInsets = WindowInsets.statusBars,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(),
        actions = {
            if (onAboutClick != null) {
                IconButton(
                    onClick = onAboutClick,
                    modifier = Modifier.testTag(AppTopBarAboutActionTestTag),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = aboutContentDescription,
                    )
                }
            }
            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag(AppTopBarSettingsActionTestTag),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = settingsContentDescription,
                    )
                }
            }
        },
    )
}
