package ru.dmdp.tishina.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.feature.about.AboutScreen
import ru.dmdp.tishina.feature.history.HistoryScreen
import ru.dmdp.tishina.feature.history.detail.DetailRoute
import ru.dmdp.tishina.feature.history.detail.DetailScreen
import ru.dmdp.tishina.feature.measure.MeasureScreen
import ru.dmdp.tishina.feature.settings.SettingsScreen

@Composable
fun TishinaNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onApplyLocale: (AppLocale) -> Unit = {},
    measureContent: @Composable () -> Unit = { MeasureScreen() },
    // Stub slots mirror the `measureContent` pattern from Phase 2 — they let unit tests
    // exercise the NavHost graph without standing up Hilt-injected ViewModels (which would
    // pull in Room and the rest of the data layer). Production callers stick with the
    // defaults, which delegate to the real Hilt-aware screens.
    historyContent: @Composable (
        onNavigateToDetail: (Long) -> Unit,
        onNavigateToMeasure: () -> Unit,
    ) -> Unit = { onNavigateToDetail, onNavigateToMeasure ->
        HistoryScreen(
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToMeasure = onNavigateToMeasure,
        )
    },
    detailContent: @Composable (
        measurementId: Long,
        onNavigateBack: () -> Unit,
    ) -> Unit = { _, onNavigateBack ->
        // DetailViewModel decodes the id itself from SavedStateHandle.toRoute<DetailRoute>(),
        // so the production composable does NOT need the id passed in. The stub seat is
        // exposed only for tests that want to assert routing decoded the right value.
        DetailScreen(onNavigateBack = onNavigateBack)
    },
    settingsContent: @Composable (onApplyLocale: (AppLocale) -> Unit) -> Unit = { applyLocale ->
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() },
            onApplyLocale = applyLocale,
        )
    },
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
            historyContent(
                { id -> navController.navigate(DetailRoute(measurementId = id)) },
                // Empty-state CTA → switch to the Measure tab using the same top-level navigation
                // policy as the bottom NavigationBar / NavigationRail. A plain navigate(Measure)
                // would push a duplicate Measure entry above History so Back from Measure would
                // pop back to History — that's not how a top-level tab switch should behave.
                { navController.navigateToTopLevel(TishinaDestination.Measure) },
            )
        }
        composable<DetailRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DetailRoute>()
            detailContent(route.measurementId) { navController.popBackStack() }
        }
        composable<TishinaDestination.Settings> {
            settingsContent(onApplyLocale)
        }
        composable<TishinaDestination.About> {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
