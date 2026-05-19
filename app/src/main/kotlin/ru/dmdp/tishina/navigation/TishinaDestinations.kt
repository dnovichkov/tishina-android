package ru.dmdp.tishina.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
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
 * NavigationRail (medium/expanded). [TishinaDestination.About] is intentionally
 * reachable only from the TopAppBar action and is therefore not in this list.
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
