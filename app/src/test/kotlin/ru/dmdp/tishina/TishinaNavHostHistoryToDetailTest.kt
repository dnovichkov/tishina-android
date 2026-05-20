package ru.dmdp.tishina

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.feature.history.detail.DetailRoute
import ru.dmdp.tishina.navigation.TishinaDestination
import ru.dmdp.tishina.navigation.TishinaNavHost

/**
 * Verifies the History → Detail → Back navigation contract introduced in Phase 3 Task 7
 * without touching Hilt-injected graphs. Uses stub composables for `historyContent` and
 * `detailContent` (same pattern as `measureContent` from Phase 2) so the NavHost behaviour
 * can be exercised in isolation from ViewModels/Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class TishinaNavHostHistoryToDetailTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val historyStubTag = "history_stub"
    private val historyToDetailButtonTag = "history_stub_open_detail"
    private val detailStubTag = "detail_stub"
    private val detailIdTextTag = "detail_stub_id_text"

    private val seededMeasurementId = 42L

    @Test
    fun `navigates from History to Detail and Back returns to History`() {
        var capturedController: NavHostController? = null
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val navController = rememberNavController()
                capturedController = navController
                Box(modifier = Modifier.fillMaxSize()) {
                    TishinaNavHost(
                        navController = navController,
                        // Bypass MeasureScreen — Phase 3 only touches History/Detail routes.
                        measureContent = { Box(modifier = Modifier.fillMaxSize()) },
                        historyContent = { onNavigateToDetail, _ ->
                            HistoryStub(onOpenDetail = { onNavigateToDetail(seededMeasurementId) })
                        },
                        detailContent = { measurementId, _ ->
                            DetailStub(measurementId = measurementId)
                        },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Manually navigate to History (no NavigationBar in this minimal harness).
        composeTestRule.runOnUiThread {
            capturedController!!.navigate(TishinaDestination.History)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(historyStubTag).assertIsDisplayed()

        // Tap the stub History "open detail" CTA → triggers onNavigateToDetail(42L).
        composeTestRule.onNodeWithTag(historyToDetailButtonTag).performClick()
        composeTestRule.waitForIdle()

        // Detail destination should now be on top of the back stack with the correct id.
        composeTestRule.onNodeWithTag(detailStubTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(detailIdTextTag).assertIsDisplayed()
        val currentAfterNav = capturedController!!.currentBackStackEntry?.destination
        assertNotNull("Detail destination must exist", currentAfterNav)
        assertTrue(
            "After clicking the stub CTA the destination should be DetailRoute",
            currentAfterNav!!.hasRoute(DetailRoute::class),
        )

        // Press the NavController back — should return to History (still on the stack).
        composeTestRule.runOnUiThread {
            capturedController!!.popBackStack()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(historyStubTag).assertIsDisplayed()
        val currentAfterBack = capturedController!!.currentBackStackEntry?.destination
        assertTrue(
            "Back from Detail must return to History",
            currentAfterBack!!.hasRoute(TishinaDestination.History::class),
        )
    }

    @Test
    fun `Detail composable decodes measurementId from type-safe route`() {
        // Regression guard for the @Serializable contract of DetailRoute: the id passed via
        // navController.navigate(DetailRoute(id)) must be retrievable on the receiving side
        // (DetailViewModel uses the same SavedStateHandle.toRoute<DetailRoute>() decode path).
        val expectedId = 7777L
        var capturedId: Long? = null
        var capturedController: NavHostController? = null
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val navController = rememberNavController()
                capturedController = navController
                Box(modifier = Modifier.fillMaxSize()) {
                    TishinaNavHost(
                        navController = navController,
                        measureContent = { Box(modifier = Modifier.fillMaxSize()) },
                        historyContent = { _, _ -> Box(modifier = Modifier.fillMaxSize()) },
                        detailContent = { measurementId, _ ->
                            capturedId = measurementId
                            Box(modifier = Modifier.fillMaxSize())
                        },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnUiThread {
            capturedController!!.navigate(DetailRoute(measurementId = expectedId))
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "DetailRoute measurementId must round-trip through the NavController",
            expectedId,
            capturedId,
        )
    }

    @Composable
    private fun HistoryStub(onOpenDetail: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().testTag(historyStubTag)) {
            Button(
                onClick = onOpenDetail,
                modifier = Modifier.testTag(historyToDetailButtonTag),
            ) {
                Text(text = "open detail")
            }
        }
    }

    @Composable
    private fun DetailStub(measurementId: Long) {
        Box(modifier = Modifier.fillMaxSize().testTag(detailStubTag)) {
            Text(
                text = "detail:$measurementId",
                modifier = Modifier.testTag(detailIdTextTag),
            )
        }
    }
}
