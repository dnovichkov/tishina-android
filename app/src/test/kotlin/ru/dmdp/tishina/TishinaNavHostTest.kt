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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.ui.components.PlaceholderTitleTestTag
import ru.dmdp.tishina.navigation.TishinaDestination
import ru.dmdp.tishina.navigation.TopLevelDestination
import ru.dmdp.tishina.ui.TishinaApp
import ru.dmdp.tishina.ui.TishinaAppRootTestTag
import ru.dmdp.tishina.ui.TishinaNavigationBarTestTag
import ru.dmdp.tishina.ui.navigationItemTestTag

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class TishinaNavHostTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `start destination is Measure`() {
        var capturedController: NavHostController? = null
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val navController = rememberNavController()
                capturedController = navController
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
                TishinaApp(windowSizeClass = sizeClass, navController = navController)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TishinaAppRootTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TishinaNavigationBarTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(PlaceholderTitleTestTag).assertIsDisplayed()

        val current = capturedController!!.currentBackStackEntry?.destination
        assertNotNull("Current destination must exist", current)
        assertTrue(
            "Start destination must be Measure",
            current!!.hasRoute(TishinaDestination.Measure::class),
        )
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `clicking History navigation item navigates to History`() {
        var capturedController: NavHostController? = null
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val navController = rememberNavController()
                capturedController = navController
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
                TishinaApp(windowSizeClass = sizeClass, navController = navController)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(navigationItemTestTag(TopLevelDestination.History))
            .performClick()
        composeTestRule.waitForIdle()

        val current = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "After clicking History the destination should be History",
            current!!.hasRoute(TishinaDestination.History::class),
        )
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `top bar about action navigates to About`() {
        var capturedController: NavHostController? = null
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val navController = rememberNavController()
                capturedController = navController
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
                TishinaApp(windowSizeClass = sizeClass, navController = navController)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(ru.dmdp.tishina.ui.TishinaAboutActionTestTag)
            .performClick()
        composeTestRule.waitForIdle()

        val current = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "After clicking About action the destination should be About",
            current!!.hasRoute(TishinaDestination.About::class),
        )
    }
}
