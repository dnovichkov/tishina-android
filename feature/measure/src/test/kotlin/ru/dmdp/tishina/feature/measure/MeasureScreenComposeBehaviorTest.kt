package ru.dmdp.tishina.feature.measure

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.feature.measure.ui.MeasureBottomBarSaveTestTag
import ru.dmdp.tishina.feature.measure.ui.MeasureBottomBarStartPauseFabTestTag
import ru.dmdp.tishina.feature.measure.ui.PermissionRationaleDialogTestTag

/**
 * Verifies the visual contract of the Measure screen without relying on a real ViewModel:
 *  - the FAB is clickable in Idle,
 *  - the Save action is disabled in Phase 2,
 *  - the rationale dialog renders when [showRationale] is true.
 *
 * Driving [MeasureScreenContent] directly keeps the test fast and isolates it from the
 * permission launcher / Hilt graph — those concerns live in [MeasureScreen] proper and are
 * exercised by integration-style work in Task 11.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class MeasureScreenComposeBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun idle_fab_is_clickable_and_save_is_disabled() {
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                MeasureScreenContent(
                    state = MeasureUiState(
                        phase = MeasurementPhase.Idle,
                        permissionState = PermissionState.Unknown,
                    ),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    showRationale = false,
                    onRationaleConfirm = {},
                    onRationaleDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(MeasureBottomBarStartPauseFabTestTag)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule.onNodeWithTag(MeasureBottomBarSaveTestTag)
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun rationale_dialog_renders_when_show_rationale_is_true() {
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                MeasureScreenContent(
                    state = MeasureUiState(
                        phase = MeasurementPhase.Idle,
                        permissionState = PermissionState.Denied,
                    ),
                    onEvent = {},
                    snackbarHostState = SnackbarHostState(),
                    showRationale = true,
                    onRationaleConfirm = {},
                    onRationaleDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(PermissionRationaleDialogTestTag).assertIsDisplayed()
    }

    @Test
    fun start_pause_fab_routes_to_start_in_idle_phase() {
        var lastEvent: MeasureUiEvent? = null
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                MeasureScreenContent(
                    state = MeasureUiState(
                        phase = MeasurementPhase.Idle,
                        permissionState = PermissionState.Granted,
                    ),
                    onEvent = { lastEvent = it },
                    snackbarHostState = SnackbarHostState(),
                    showRationale = false,
                    onRationaleConfirm = {},
                    onRationaleDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(MeasureBottomBarStartPauseFabTestTag).performClick()
        assert(lastEvent == MeasureUiEvent.StartRequested) {
            "Expected StartRequested, got $lastEvent"
        }
    }

    @Test
    fun start_pause_fab_routes_to_pause_when_running() {
        var lastEvent: MeasureUiEvent? = null
        composeTestRule.setContent {
            TishinaTheme(dynamicColor = false) {
                MeasureScreenContent(
                    state = MeasureUiState(
                        current = 60f,
                        phase = MeasurementPhase.Running,
                        permissionState = PermissionState.Granted,
                    ),
                    onEvent = { lastEvent = it },
                    snackbarHostState = SnackbarHostState(),
                    showRationale = false,
                    onRationaleConfirm = {},
                    onRationaleDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(MeasureBottomBarStartPauseFabTestTag).performClick()
        assert(lastEvent == MeasureUiEvent.PauseRequested) {
            "Expected PauseRequested, got $lastEvent"
        }
    }
}
