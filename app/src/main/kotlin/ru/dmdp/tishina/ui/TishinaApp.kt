package ru.dmdp.tishina.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.dmdp.tishina.R

/**
 * Foundation-phase stub. Real adaptive NavigationBar/NavigationRail + NavHost
 * land in Task 5 of the foundation plan.
 */
@Composable
fun TishinaApp() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .testTag(TishinaAppRootTestTag),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(id = R.string.app_full_name))
        }
    }
}

const val TishinaAppRootTestTag: String = "tishina_app_root"
