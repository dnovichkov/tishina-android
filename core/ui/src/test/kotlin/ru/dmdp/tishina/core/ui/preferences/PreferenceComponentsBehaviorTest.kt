package ru.dmdp.tishina.core.ui.preferences

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme

/**
 * Behavioural smoke for the reusable preference primitives. Roborazzi covers
 * pixels — this suite covers the contract (clicks emit, headers render, test
 * tags are reachable). Without it, a silent breakage of `onValueChange` would
 * only surface in screenshots which can't catch logic regressions.
 */
@RunWith(RobolectricTestRunner::class)
class PreferenceComponentsBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `PreferenceCategory renders title above its slot`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                PreferenceCategory(title = "Measurement") {
                    androidx.compose.material3.Text(text = "Body content")
                }
            }
        }

        composeTestRule.onNodeWithTag(PreferenceCategoryTitleTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithText("Measurement").assertIsDisplayed()
        composeTestRule.onNodeWithText("Body content").assertIsDisplayed()
    }

    @Test
    fun `ChoicePreference invokes callback with selected option`() {
        var picked: String? = null
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                MaterialTheme {
                    ChoicePreference(
                        title = "Theme",
                        options = listOf("System", "Light", "Dark"),
                        optionLabels = listOf("System", "Light", "Dark"),
                        selectedOption = "System",
                        onOptionSelected = { picked = it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Dark").performClick()
        assertEquals("Dark", picked)
    }

    @Test
    fun `SwitchPreference toggles via row click`() {
        var toggled: Boolean? = null
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SwitchPreference(
                    title = "Dynamic colors",
                    subtitle = "Material You",
                    checked = false,
                    onCheckedChange = { toggled = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(SwitchPreferenceTestTag).performClick()
        assertEquals(true, toggled)
    }

    @Test
    fun `SwitchPreference disabled row does not fire callback`() {
        var fired = false
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SwitchPreference(
                    title = "Dynamic colors",
                    subtitle = "Available on Android 12+",
                    checked = false,
                    enabled = false,
                    onCheckedChange = { fired = true },
                )
            }
        }

        composeTestRule.onNodeWithTag(SwitchPreferenceTestTag).performClick()
        assertEquals(false, fired)
    }

    @Test
    fun `SliderPreference reset button invokes callback`() {
        var resetCalled = false
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SliderPreference(
                    title = "Calibration",
                    value = 5f,
                    onValueChange = {},
                    valueRange = -20f..20f,
                    steps = 400,
                    valueFormatter = { "%+.1f dB".format(it) },
                    onResetClick = { resetCalled = true },
                    resetButtonLabel = "Reset calibration",
                )
            }
        }

        composeTestRule.onNodeWithText("Reset calibration").performClick()
        assertTrue(resetCalled)
    }
}
