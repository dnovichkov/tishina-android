package ru.dmdp.tishina.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.ui.preferences.SliderPreferenceResetTestTag
import ru.dmdp.tishina.core.ui.preferences.SliderPreferenceSliderTestTag
import ru.dmdp.tishina.core.ui.preferences.SliderPreferenceValueTestTag

/**
 * UI contract for the calibration slider inside SettingsScreen (FR-14, FR-19).
 *
 * Why `performSemanticsAction(SetProgress)` instead of touch gestures: M3 Slider's
 * accessibility action invokes both `onValueChange` and `onValueChangeFinished`
 * in a single deterministic call, plus it does the value clamping for us. Touch
 * swipes are viewport-sensitive under Robolectric and flake on the LazyColumn
 * scroll position — Tasks 4 already had to scroll controls into view before
 * tapping. SetProgress sidesteps that whole class of flakiness.
 *
 * The `setProgress` action returns false when the resolved (post-step,
 * post-coerce) value matches the current one — that's why we assert on the
 * recorded event list rather than on the action's boolean return.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenCalibrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `initial calibration zero shows formatted dB label`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, calibrationOffsetDb = 0f),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        // Value label is the formatted `%+.1f dB` — matches strings.xml format.
        composeTestRule.onNodeWithTag(SliderPreferenceValueTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithText("+0.0 dB").assertIsDisplayed()
    }

    @Test
    fun `non-zero calibration shows correctly signed dB label`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, calibrationOffsetDb = 15f),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("+15.0 dB").assertIsDisplayed()
    }

    @Test
    fun `negative calibration label has explicit minus sign`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, calibrationOffsetDb = -8f),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("-8.0 dB").assertIsDisplayed()
    }

    @Test
    fun `setProgress on calibration slider emits ChangeCalibration event`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, calibrationOffsetDb = 0f),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(SliderPreferenceSliderTestTag)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(5.5f) }

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeCalibration }
        assertNotNull("Expected ChangeCalibration to be emitted, got $events", event)
        // Slider has 399 inner steps over 40 dB → step = 40/400 = 0.1 dB.
        // Target 5.5 lands on a step boundary so it's preserved verbatim.
        assertEquals(5.5f, event!!.db, 0.05f)
    }

    @Test
    fun `setProgress beyond max calibration is clamped to plus twenty`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, calibrationOffsetDb = 0f),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        // Asking for +50 dB — M3 Slider coerces into the configured range first.
        composeTestRule.onNodeWithTag(SliderPreferenceSliderTestTag)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(50f) }

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeCalibration }
        assertNotNull("Expected ChangeCalibration even for out-of-range request, got $events", event)
        assertEquals(20f, event!!.db, 0.05f)
    }

    @Test
    fun `setProgress below min calibration is clamped to minus twenty`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, calibrationOffsetDb = 0f),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(SliderPreferenceSliderTestTag)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(-50f) }

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeCalibration }
        assertNotNull(event)
        assertEquals(-20f, event!!.db, 0.05f)
    }

    @Test
    fun `reset button click emits ResetCalibration event`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, calibrationOffsetDb = 7.5f),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(SliderPreferenceResetTestTag).performClick()

        assertTrue(
            "Expected ResetCalibration in $events",
            events.any { it is SettingsUiEvent.ResetCalibration },
        )
    }

    @Test
    fun `calibration description text is visible for accessibility`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, calibrationOffsetDb = 0f),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        // String comes from values/strings.xml — locks the FR-14 explanation in place.
        composeTestRule.onNodeWithText(
            "Compare with a reference sound-level meter and shift readings by this offset.",
        ).assertIsDisplayed()
    }
}
