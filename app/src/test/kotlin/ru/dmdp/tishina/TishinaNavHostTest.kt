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
import ru.dmdp.tishina.navigation.TishinaDestination
import ru.dmdp.tishina.navigation.TopLevelDestination
import ru.dmdp.tishina.testutils.AboutScreenTestStub
import ru.dmdp.tishina.testutils.AboutStubBackTestTag
import ru.dmdp.tishina.testutils.HistoryEmptyCtaStubTestTag
import ru.dmdp.tishina.testutils.HistoryScreenTestStub
import ru.dmdp.tishina.testutils.MeasureContentStubTestTag
import ru.dmdp.tishina.testutils.MeasureScreenTestStub
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
                TishinaApp(
                    windowSizeClass = sizeClass,
                    navController = navController,
                    measureContent = { MeasureScreenTestStub() },
                    historyContent = { _, _ -> HistoryScreenTestStub() },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TishinaAppRootTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TishinaNavigationBarTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MeasureContentStubTestTag).assertIsDisplayed()

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

        val current = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "After clicking History the destination should be History",
            current!!.hasRoute(TishinaDestination.History::class),
        )
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `top bar disclaimer action on Measure does not navigate away`() {
        // FR-22: on the Measure tab the «?» icon opens the accuracy disclaimer BottomSheet
        // (TishinaAppDisclaimerWiringTest covers the sheet itself). It must NOT change the
        // current destination — pre-Phase-5 the same icon navigated straight to About, which
        // this test guards against regressing back to.
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

        composeTestRule
            .onNodeWithTag(ru.dmdp.tishina.ui.TishinaDisclaimerActionTestTag)
            .performClick()
        composeTestRule.waitForIdle()

        val current = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "Clicking the disclaimer icon must keep us on Measure (sheet, not navigation)",
            current!!.hasRoute(TishinaDestination.Measure::class),
        )
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `back from About returns to the originating non-start destination`() {
        // Regression guard: About is a detail screen, not a top-level entry. Reaching About
        // from History must NOT discard History from the back stack — Back must land back
        // on History (not on the start destination Measure). Phase 5 removed the global «?»
        // icon from History (FR-22 sheet is Measure-only), so we drive the navigation via
        // the same callback the BottomSheet's "Learn more" button uses in production. The
        // outer TopBar is fully suppressed on About (AboutScreen brings its own Scaffold +
        // back arrow), so the test stub exposes a clickable back affordance that fires the
        // same `onNavigateBack` callback the real screen wires into its leading TopAppBar
        // icon.
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
                    aboutContent = { onNavigateBack -> AboutScreenTestStub(onNavigateBack) },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Navigate Measure → History via the NavigationBar item.
        composeTestRule
            .onNodeWithTag(navigationItemTestTag(TopLevelDestination.History))
            .performClick()
        composeTestRule.waitForIdle()

        // From History, navigate to About the same way production does (single-top, no
        // back-stack flattening) — emulating the Settings footer / BottomSheet "Learn more"
        // entry points that survive Phase 5.
        composeTestRule.runOnUiThread {
            capturedController!!.navigate(TishinaDestination.About) {
                launchSingleTop = true
            }
        }
        composeTestRule.waitForIdle()

        // Press Back from About via the stub's back-affordance — same callback the real
        // AboutScreen TopAppBar back arrow invokes in production.
        composeTestRule.onNodeWithTag(AboutStubBackTestTag).performClick()
        composeTestRule.waitForIdle()

        val current = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "Back from About launched from History must return to History, not to Measure",
            current!!.hasRoute(TishinaDestination.History::class),
        )
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `History empty-state CTA uses top-level navigation to Measure (back stack stays shallow)`() {
        // Regression for codex review finding: History → Measure CTA used a plain navigate(Measure)
        // that pushed a duplicate Measure entry above History. After the fix, the CTA routes
        // through `navigateToTopLevel(Measure)` — same policy as the NavigationBar — so the back
        // stack collapses to [Measure] instead of [Measure, History, Measure].
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
                    historyContent = { onNavigateToDetail, onNavigateToMeasure ->
                        HistoryScreenTestStub(
                            onNavigateToDetail = onNavigateToDetail,
                            onNavigateToMeasure = onNavigateToMeasure,
                        )
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        // Switch to History via the NavigationBar — the start destination is Measure.
        composeTestRule
            .onNodeWithTag(navigationItemTestTag(TopLevelDestination.History))
            .performClick()
        composeTestRule.waitForIdle()

        // Click the empty-state CTA via the stub's clickable region.
        composeTestRule.onNodeWithTag(HistoryEmptyCtaStubTestTag).performClick()
        composeTestRule.waitForIdle()

        // The CTA must land us on Measure (top-level switch).
        val afterCta = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "After CTA the destination should be Measure, got ${afterCta?.route}",
            afterCta!!.hasRoute(TishinaDestination.Measure::class),
        )

        // popUpTo(startId) collapsed History — popping from Measure must NOT resurface History.
        // (Without the fix the stack would be [Measure, History, Measure] and a pop would land
        // back on History.)
        composeTestRule.runOnUiThread {
            capturedController!!.popBackStack()
        }
        composeTestRule.waitForIdle()
        val afterPop = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "After popping from Measure we should NOT be on History, got ${afterPop?.route}",
            afterPop == null || !afterPop.hasRoute(TishinaDestination.History::class),
        )
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `outer top bar is suppressed on About route and the stub back affordance pops`() {
        // AboutScreen brings its own Scaffold + TopAppBar (back arrow + title) — same pattern
        // as DetailScreen and SettingsScreen. So on the About route the outer TishinaTopAppBar
        // must be fully suppressed; otherwise users see two stacked title bars on phones.
        // The stub stands in for the real screen here, but the navigation contract under test
        // is the same: outer chrome gone, back affordance lives inside the screen.
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
                    aboutContent = { onNavigateBack -> AboutScreenTestStub(onNavigateBack) },
                )
            }
        }
        composeTestRule.waitForIdle()

        // On Measure the outer TopBar is displayed with the disclaimer action.
        composeTestRule.onNodeWithTag(ru.dmdp.tishina.ui.TishinaTopAppBarTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ru.dmdp.tishina.ui.TishinaDisclaimerActionTestTag).assertIsDisplayed()

        // Navigate to About the same way production does (matches `navigateToAbout()` helper —
        // see Settings footer link / FR-22 BottomSheet "Learn more" entry points).
        composeTestRule.runOnUiThread {
            capturedController!!.navigate(TishinaDestination.About) {
                launchSingleTop = true
            }
        }
        composeTestRule.waitForIdle()

        // On the About route the outer TopBar is gone entirely — AboutScreen brings its own.
        composeTestRule.onNodeWithTag(ru.dmdp.tishina.ui.TishinaTopAppBarTestTag).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ru.dmdp.tishina.ui.TishinaDisclaimerActionTestTag).assertDoesNotExist()

        // The stub's back affordance fires `onNavigateBack`, which pops the back stack the
        // same way the real screen's TopAppBar back arrow does.
        composeTestRule.onNodeWithTag(AboutStubBackTestTag).performClick()
        composeTestRule.waitForIdle()

        // Back action must pop the About destination — we land back on Measure (the start dest).
        val current = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "After Back from About we must return to the start destination Measure",
            current!!.hasRoute(TishinaDestination.Measure::class),
        )
        // And on Measure the outer TopBar with the disclaimer action reappears.
        composeTestRule.onNodeWithTag(ru.dmdp.tishina.ui.TishinaTopAppBarTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ru.dmdp.tishina.ui.TishinaDisclaimerActionTestTag).assertIsDisplayed()
    }
}
