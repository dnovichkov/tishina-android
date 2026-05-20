package ru.dmdp.tishina.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable
import ru.dmdp.tishina.core.ui.R as CoreUiR

/**
 * Type-safe destinations rendered by `androidx.navigation.compose` 2.9+.
 * The `@Serializable` annotation lets the NavHost generate route patterns
 * automatically without string constants.
 */
sealed interface TishinaDestination {

    @Serializable
    data object Measure : TishinaDestination

    @Serializable
    data object History : TishinaDestination

    @Serializable
    data object Settings : TishinaDestination

    @Serializable
    data object About : TishinaDestination
}

/**
 * Subset of destinations that appear in the bottom NavigationBar (compact) or
 * NavigationRail (medium/expanded). [TishinaDestination.About] is rendered
 * separately — as a TopAppBar action in compact layout, and as an additional
 * NavigationRailItem (below this list) in medium/expanded layout — so it is
 * not part of the main destination set.
 */
enum class TopLevelDestination(val destination: TishinaDestination, @StringRes val labelRes: Int, val icon: ImageVector) {
    Measure(
        destination = TishinaDestination.Measure,
        labelRes = CoreUiR.string.measure_title,
        icon = Icons.Outlined.Equalizer,
    ),
    History(
        destination = TishinaDestination.History,
        labelRes = CoreUiR.string.history_title,
        icon = Icons.Outlined.History,
    ),
    Settings(
        destination = TishinaDestination.Settings,
        labelRes = CoreUiR.string.settings_title,
        icon = Icons.Outlined.Settings,
    ),
}

@StringRes
val AboutLabelRes: Int = CoreUiR.string.about_title

val AboutIcon: ImageVector = Icons.Outlined.Info

/**
 * Top-level destination navigation policy — used by both the NavigationBar/Rail clicks
 * (which switch between Measure/History/Settings tabs) and by inline calls-to-action that
 * route the user back to a top-level entry (e.g. History's empty-state "Make first
 * measurement" button — without this policy, that CTA pushes a duplicate Measure entry
 * onto the back stack and Back returns to History instead of exiting like a normal tab
 * switch).
 *
 * `popUpTo(startId) { saveState = true }` keeps the back stack shallow (one entry per
 * top-level destination) and preserves each tab's internal state on switch.
 * `launchSingleTop = true` prevents stacking another copy of the same destination on
 * top of itself. `restoreState = true` re-attaches the saved tab state on return.
 *
 * `graph.startDestinationId` is read with a `runCatching` guard because the graph is
 * not set until NavHost composes its first pass; a synthetic accessibility click on a
 * navigation item before that frame would throw IllegalStateException ("setGraph must
 * be called"). If the graph isn't ready, the call is a no-op until the next frame.
 */
fun NavHostController.navigateToTopLevel(destination: TishinaDestination) {
    val startId = runCatching { graph.startDestinationId }.getOrNull() ?: return
    navigate(destination) {
        launchSingleTop = true
        restoreState = true
        popUpTo(startId) { saveState = true }
    }
}
