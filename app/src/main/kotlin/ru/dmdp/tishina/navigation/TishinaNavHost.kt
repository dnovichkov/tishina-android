package ru.dmdp.tishina.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import ru.dmdp.tishina.feature.about.AboutScreen
import ru.dmdp.tishina.feature.history.HistoryScreen
import ru.dmdp.tishina.feature.measure.MeasureScreen
import ru.dmdp.tishina.feature.settings.SettingsScreen

@Composable
fun TishinaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TishinaDestination.Measure,
        modifier = modifier,
    ) {
        composable<TishinaDestination.Measure> {
            MeasureScreen(
                onNavigateToAbout = { navController.navigate(TishinaDestination.About) },
                onNavigateToSettings = { navController.navigate(TishinaDestination.Settings) },
            )
        }
        composable<TishinaDestination.History> {
            HistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<TishinaDestination.Settings> {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<TishinaDestination.About> {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
