package ru.dmdp.tishina.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.material3.ScaffoldDefaults
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
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.feature.history.detail.DetailRoute
import ru.dmdp.tishina.feature.measure.MeasureScreen
import ru.dmdp.tishina.feature.settings.SettingsScreen
import ru.dmdp.tishina.navigation.AboutIcon
import ru.dmdp.tishina.navigation.AboutLabelRes
import ru.dmdp.tishina.navigation.TishinaDestination
import ru.dmdp.tishina.navigation.TishinaNavHost
import ru.dmdp.tishina.navigation.TopLevelDestination
import ru.dmdp.tishina.navigation.navigateToTopLevel
import ru.dmdp.tishina.core.ui.R as CoreUiR

const val TishinaAppRootTestTag: String = "tishina_app_root"
const val TishinaNavigationBarTestTag: String = "tishina_navigation_bar"
const val TishinaNavigationRailTestTag: String = "tishina_navigation_rail"
const val TishinaAboutActionTestTag: String = "tishina_about_action"
const val TishinaBackActionTestTag: String = "tishina_back_action"
const val TishinaTopAppBarTestTag: String = "tishina_top_app_bar"

fun navigationItemTestTag(destination: TopLevelDestination): String =
    "tishina_nav_item_${destination.name.lowercase()}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TishinaApp(
    windowSizeClass: WindowSizeClass,
    navController: NavHostController = rememberNavController(),
    // FR-18 — wired from `MainActivity` to `LocaleSwitcher::apply`. Defaulted to a no-op so
    // tests that only care about layout/navigation don't have to specify it.
    onApplyLocale: (AppLocale) -> Unit = {},
    measureContent: @Composable () -> Unit = { MeasureScreen() },
    // Same stub-slot pattern as `measureContent` — production callers omit them and get the
    // real Hilt-injected screens. Tests pass simpler composables to avoid standing up the
    // Hilt graph for ViewModels they aren't asserting against.
    historyContent: @Composable (
        onNavigateToDetail: (Long) -> Unit,
        onNavigateToMeasure: () -> Unit,
    ) -> Unit = { onNavigateToDetail, onNavigateToMeasure ->
        ru.dmdp.tishina.feature.history.HistoryScreen(
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToMeasure = onNavigateToMeasure,
        )
    },
    detailContent: @Composable (
        measurementId: Long,
        onNavigateBack: () -> Unit,
    ) -> Unit = { _, onNavigateBack ->
        ru.dmdp.tishina.feature.history.detail.DetailScreen(onNavigateBack = onNavigateBack)
    },
    settingsContent: @Composable (onApplyLocale: (AppLocale) -> Unit) -> Unit = { applyLocale ->
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() },
            onApplyLocale = applyLocale,
        )
    },
) {
    val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onAboutClick = remember(navController) {
        { navController.navigateToAbout() }
    }
    val onBackClick: () -> Unit = remember(navController) {
        {
            navController.popBackStack()
        }
    }
    val isOnAbout = currentDestination.matchesAbout()
    // DetailScreen brings its own Scaffold + TopAppBar (VM-driven title plus back/delete
    // actions). Suppress the outer chrome here so the two TopAppBars don't stack on phones.
    val isOnDetail = currentDestination.matchesDetail()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TishinaAppRootTestTag),
    ) {
        if (useNavigationRail) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
            ) {
                TishinaNavigationRail(
                    currentDestination = currentDestination,
                    onItemSelected = { dest -> navController.navigateTopLevel(dest) },
                    onAboutClick = onAboutClick,
                )
                Box(modifier = Modifier.fillMaxSize()) {
                    TishinaNavHost(
                        navController = navController,
                        onApplyLocale = onApplyLocale,
                        measureContent = measureContent,
                        historyContent = historyContent,
                        detailContent = detailContent,
                        settingsContent = settingsContent,
                    )
                }
            }
        } else {
            Scaffold(
                topBar = {
                    if (!isOnDetail) {
                        TishinaTopAppBar(
                            currentDestination = currentDestination,
                            isOnAbout = isOnAbout,
                            onAboutClick = onAboutClick,
                            onBackClick = onBackClick,
                        )
                    }
                },
                bottomBar = {
                    if (!isOnDetail) {
                        TishinaNavigationBar(
                            currentDestination = currentDestination,
                            onItemSelected = { dest -> navController.navigateTopLevel(dest) },
                        )
                    }
                },
                // Detail brings its own Scaffold with full system-bar handling, so we zero out
                // the outer Scaffold's contentWindowInsets to avoid double-padding the status
                // bar. Top-level destinations still consume insets via their bars.
                contentWindowInsets = if (isOnDetail) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    TishinaNavHost(
                        navController = navController,
                        onApplyLocale = onApplyLocale,
                        measureContent = measureContent,
                        historyContent = historyContent,
                        detailContent = detailContent,
                        settingsContent = settingsContent,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TishinaTopAppBar(
    currentDestination: NavDestination?,
    isOnAbout: Boolean,
    onAboutClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    val titleRes = currentDestination.topAppBarTitleRes()
    CenterAlignedTopAppBar(
        title = { Text(text = stringResource(id = titleRes)) },
        modifier = Modifier.testTag(TishinaTopAppBarTestTag),
        navigationIcon = {
            // On the About route the About IconButton would be a visible no-op (re-navigating
            // to the current destination with launchSingleTop does nothing); replace it with
            // a back affordance so the primary action is real.
            if (isOnAbout) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag(TishinaBackActionTestTag),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(id = CoreUiR.string.nav_back),
                    )
                }
            }
        },
        actions = {
            if (!isOnAbout) {
                IconButton(
                    onClick = onAboutClick,
                    modifier = Modifier.testTag(TishinaAboutActionTestTag),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(id = CoreUiR.string.nav_open_about),
                    )
                }
            }
        },
    )
}

private fun NavDestination?.topAppBarTitleRes(): Int {
    if (this != null) {
        TopLevelDestination.entries.firstOrNull { matches(it) }?.let { return it.labelRes }
        if (matchesAbout()) return AboutLabelRes
    }
    return TopLevelDestination.Measure.labelRes
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
    return when (item.destination) {
        TishinaDestination.Measure -> hasRoute(TishinaDestination.Measure::class)
        TishinaDestination.History -> hasRoute(TishinaDestination.History::class)
        TishinaDestination.Settings -> hasRoute(TishinaDestination.Settings::class)
        TishinaDestination.About -> hasRoute(TishinaDestination.About::class)
    }
}

private fun NavDestination?.matchesAbout(): Boolean {
    if (this == null) return false
    return hasRoute(TishinaDestination.About::class)
}

private fun NavDestination?.matchesDetail(): Boolean {
    if (this == null) return false
    return hasRoute(DetailRoute::class)
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigateToTopLevel(destination.destination)
}

private fun NavHostController.navigateToAbout() {
    // About is a detail screen — not a top-level entry like Measure/History/Settings —
    // so it must sit on top of whichever screen launched it. The `popUpTo(startId)`
    // pattern from `navigateTopLevel` would discard the originating entry (e.g. History),
    // breaking Back navigation: user on History → tap About → Back would land on Measure
    // instead of History. The crash guard against `graph` access before NavHost composes
    // is still needed.
    runCatching { graph.startDestinationId }.getOrNull() ?: return
    navigate(TishinaDestination.About) {
        launchSingleTop = true
    }
}
