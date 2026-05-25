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
import ru.dmdp.tishina.ui.TishinaAboutActionTestTag
import ru.dmdp.tishina.ui.TishinaApp
import ru.dmdp.tishina.ui.navigationItemTestTag

/**
 * FR-22 wiring contract for the global accuracy disclaimer icon.
 *
 * Context-aware visibility, fixed in Phase 5:
 * - Measure tab → «?» icon visible; click opens the disclaimer sheet without navigating.
 * - History / Detail → «?» icon hidden (no semantic tie to disclaimer).
 * - Settings / About → outer TopAppBar suppressed entirely (those screens own their own
 *   bars); the icon is not relevant there.
 *
 * The actual sheet rendering is covered by the unit-level
 * [ru.dmdp.tishina.feature.measure.ui.AccuracyDisclaimerBottomSheetBehaviorTest] — ModalBottomSheet
 * draws into a sub-Window that doesn't settle under Robolectric, so we don't try to assert on
 * its inner nodes here. We only assert the icon's presence/absence and the side-effect that
 * clicking it does NOT change the current destination.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class TishinaAppDisclaimerWiringTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `disclaimer icon visible on Measure tab`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val navController = rememberNavController()
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
                TishinaApp(
                    windowSizeClass = sizeClass,
                    navController = navController,
                    measureContent = { MeasureScreenTestStub() },
                    historyContent = { _, _ -> HistoryScreenTestStub() },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TishinaAboutActionTestTag).assertIsDisplayed()
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `disclaimer icon hidden on History tab`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val navController = rememberNavController()
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
                TishinaApp(
                    windowSizeClass = sizeClass,
                    navController = navController,
                    measureContent = { MeasureScreenTestStub() },
                    historyContent = { _, _ -> HistoryScreenTestStub() },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(navigationItemTestTag(TopLevelDestination.History))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TishinaAboutActionTestTag).assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `clicking disclaimer icon does not navigate away from Measure`() {
        // Pre-Phase-5 the same icon called `navController.navigateToAbout()`. This guards
        // against the legacy behavior coming back: clicking it must keep us on Measure (the
        // BottomSheet handles the disclosure, not navigation).
        var capturedController: NavHostController? = null
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val navController = rememberNavController()
                capturedController = navController
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
                TishinaApp(
                    windowSizeClass = sizeClass,
                    navController = navController,
                    measureContent = { MeasureScreenTestStub() },
                    historyContent = { _, _ -> HistoryScreenTestStub() },
                )
            }
        }
        composeTestRule.waitForIdle()

        val before = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "Start destination must be Measure for this test",
            before!!.hasRoute(TishinaDestination.Measure::class),
        )

        composeTestRule.onNodeWithTag(TishinaAboutActionTestTag).performClick()
        composeTestRule.waitForIdle()

        val after = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "Disclaimer icon must NOT navigate (it opens a sheet); current=${after?.route}",
            after!!.hasRoute(TishinaDestination.Measure::class),
        )
    }
}
