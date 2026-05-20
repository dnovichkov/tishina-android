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
    measureContent: @Composable () -> Unit = { MeasureScreen() },
) {
    NavHost(
        navController = navController,
        startDestination = TishinaDestination.Measure,
        modifier = modifier,
    ) {
        composable<TishinaDestination.Measure> {
            measureContent()
        }
        composable<TishinaDestination.History> {
            HistoryScreen(
                // Detail route lands in Task 7; for now we still pass a real navigation hook to
                // keep History → Measure CTA working without a separate intermediate commit.
                onNavigateToDetail = { /* TODO Task 7: navController.navigate(Detail(it)) */ },
                onNavigateToMeasure = { navController.navigate(TishinaDestination.Measure) },
            )
        }
        composable<TishinaDestination.Settings> {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<TishinaDestination.About> {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
