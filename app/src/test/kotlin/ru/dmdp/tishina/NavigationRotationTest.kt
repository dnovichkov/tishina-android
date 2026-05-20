package ru.dmdp.tishina

import android.os.Build
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.navigation.TishinaDestination
import ru.dmdp.tishina.navigation.TopLevelDestination
import ru.dmdp.tishina.testutils.HistoryScreenTestStub
import ru.dmdp.tishina.testutils.MeasureScreenTestStub
import ru.dmdp.tishina.ui.TishinaApp
import ru.dmdp.tishina.ui.TishinaNavigationRailTestTag
import ru.dmdp.tishina.ui.navigationItemTestTag

// Note: real rotation (Activity.recreate) is unavailable with createComposeRule;
// this test approximates it via landscape resource qualifiers + a wide WindowSizeClass.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU], qualifiers = "w1024dp-h720dp-land")
class NavigationRotationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `landscape configuration renders NavigationRail and supports navigation`() {
        var capturedController: NavHostController? = null
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val navController = rememberNavController()
                capturedController = navController
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(1024.dp, 720.dp))
                TishinaApp(
                    windowSizeClass = sizeClass,
                    navController = navController,
                    measureContent = { MeasureScreenTestStub() },
                    historyContent = { _, _ -> HistoryScreenTestStub() },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TishinaNavigationRailTestTag).assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(navigationItemTestTag(TopLevelDestination.History))
            .performClick()
        composeTestRule.waitForIdle()

        val current = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "Navigation must work in landscape orientation",
            current!!.hasRoute(TishinaDestination.History::class),
        )
    }
}
