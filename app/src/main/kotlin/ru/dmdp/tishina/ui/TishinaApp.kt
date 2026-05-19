package ru.dmdp.tishina.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.dmdp.tishina.navigation.AboutIcon
import ru.dmdp.tishina.navigation.AboutLabelRes
import ru.dmdp.tishina.navigation.TishinaDestination
import ru.dmdp.tishina.navigation.TishinaNavHost
import ru.dmdp.tishina.navigation.TopLevelDestination
import ru.dmdp.tishina.core.ui.R as CoreUiR

const val TishinaAppRootTestTag: String = "tishina_app_root"
const val TishinaNavigationBarTestTag: String = "tishina_navigation_bar"
const val TishinaNavigationRailTestTag: String = "tishina_navigation_rail"
const val TishinaAboutActionTestTag: String = "tishina_about_action"
const val TishinaTopAppBarTestTag: String = "tishina_top_app_bar"

fun navigationItemTestTag(destination: TopLevelDestination): String =
    "tishina_nav_item_${destination.name.lowercase()}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TishinaApp(
    windowSizeClass: WindowSizeClass,
    navController: NavHostController = rememberNavController(),
) {
    val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onAboutClick = remember(navController) {
        { navController.navigate(TishinaDestination.About) { launchSingleTop = true } }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .testTag(TishinaAppRootTestTag),
    ) {
        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                TishinaNavigationRail(
                    currentDestination = currentDestination,
                    onItemSelected = { dest -> navController.navigateTopLevel(dest) },
                    onAboutClick = onAboutClick,
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    TishinaNavHost(navController = navController)
                }
            }
        } else {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(text = stringResource(id = CoreUiR.string.about_title)) },
                        modifier = Modifier.testTag(TishinaTopAppBarTestTag),
                        actions = {
                            IconButton(
                                onClick = onAboutClick,
                                modifier = Modifier.testTag(TishinaAboutActionTestTag),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                                    contentDescription = stringResource(id = CoreUiR.string.nav_open_about),
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    TishinaNavigationBar(
                        currentDestination = currentDestination,
                        onItemSelected = { dest -> navController.navigateTopLevel(dest) },
                    )
                },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    TishinaNavHost(navController = navController)
                }
            }
        }
    }
}

@Composable
private fun TishinaNavigationBar(
    currentDestination: NavDestination?,
    onItemSelected: (TopLevelDestination) -> Unit,
) {
    NavigationBar(modifier = Modifier.testTag(TishinaNavigationBarTestTag)) {
        TopLevelDestination.entries.forEach { item ->
            val selected = currentDestination.matches(item)
            NavigationBarItem(
                modifier = Modifier.testTag(navigationItemTestTag(item)),
                selected = selected,
                onClick = { onItemSelected(item) },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = { Text(text = stringResource(id = item.labelRes)) },
            )
        }
    }
}

@Composable
private fun TishinaNavigationRail(
    currentDestination: NavDestination?,
    onItemSelected: (TopLevelDestination) -> Unit,
    onAboutClick: () -> Unit,
) {
    NavigationRail(modifier = Modifier.testTag(TishinaNavigationRailTestTag)) {
        TopLevelDestination.entries.forEach { item ->
            val selected = currentDestination.matches(item)
            NavigationRailItem(
                modifier = Modifier.testTag(navigationItemTestTag(item)),
                selected = selected,
                onClick = { onItemSelected(item) },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = { Text(text = stringResource(id = item.labelRes)) },
            )
        }
        NavigationRailItem(
            modifier = Modifier.testTag(TishinaAboutActionTestTag),
            selected = currentDestination.matchesAbout(),
            onClick = onAboutClick,
            icon = { Icon(imageVector = AboutIcon, contentDescription = null) },
            label = { Text(text = stringResource(id = AboutLabelRes)) },
        )
    }
}

private fun NavDestination?.matches(item: TopLevelDestination): Boolean {
    if (this == null) return false
    return when (item) {
        TopLevelDestination.Measure -> hasRoute(TishinaDestination.Measure::class)
        TopLevelDestination.History -> hasRoute(TishinaDestination.History::class)
        TopLevelDestination.Settings -> hasRoute(TishinaDestination.Settings::class)
    }
}

private fun NavDestination?.matchesAbout(): Boolean {
    if (this == null) return false
    return hasRoute(TishinaDestination.About::class)
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    val target = when (destination) {
        TopLevelDestination.Measure -> TishinaDestination.Measure
        TopLevelDestination.History -> TishinaDestination.History
        TopLevelDestination.Settings -> TishinaDestination.Settings
    }
    navigate(target) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.startDestinationId) { saveState = true }
    }
}
