package ru.dmdp.tishina.core.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.testing.composables.PreviewSheet
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * 3 chips × 3 selected positions × light/dark = 6 baselines. Catches regressions
 * in the segmented-button styling that would otherwise only show up in the
 * SettingsScreen composite shot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ChoicePreferenceScreenshotTest {

    private val options = listOf("System", "Light", "Dark")

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun choice_first_selected_light() {
        composeTestRule.setContent {
            PreviewSheet(name = "choice_first_selected_light") {
                Sample(selectedIndex = 0)
            }
        }
        composeTestRule.onRoot().captureSnapshot("ChoicePreferenceScreenshotTest_choice_first_selected_light")
    }

    @Test
    fun choice_middle_selected_light() {
        composeTestRule.setContent {
            PreviewSheet(name = "choice_middle_selected_light") {
                Sample(selectedIndex = 1)
            }
        }
        composeTestRule.onRoot().captureSnapshot("ChoicePreferenceScreenshotTest_choice_middle_selected_light")
    }

    @Test
    fun choice_last_selected_light() {
        composeTestRule.setContent {
            PreviewSheet(name = "choice_last_selected_light") {
                Sample(selectedIndex = 2)
            }
        }
        composeTestRule.onRoot().captureSnapshot("ChoicePreferenceScreenshotTest_choice_last_selected_light")
    }

    @Test
    fun choice_first_selected_dark() {
        composeTestRule.setContent {
            PreviewSheet(name = "choice_first_selected_dark", darkTheme = true) {
                Sample(selectedIndex = 0)
            }
        }
        composeTestRule.onRoot().captureSnapshot("ChoicePreferenceScreenshotTest_choice_first_selected_dark")
    }

    @Test
    fun choice_middle_selected_dark() {
        composeTestRule.setContent {
            PreviewSheet(name = "choice_middle_selected_dark", darkTheme = true) {
                Sample(selectedIndex = 1)
            }
        }
        composeTestRule.onRoot().captureSnapshot("ChoicePreferenceScreenshotTest_choice_middle_selected_dark")
    }

    @Test
    fun choice_last_selected_dark() {
        composeTestRule.setContent {
            PreviewSheet(name = "choice_last_selected_dark", darkTheme = true) {
                Sample(selectedIndex = 2)
            }
        }
        composeTestRule.onRoot().captureSnapshot("ChoicePreferenceScreenshotTest_choice_last_selected_dark")
    }

    @Composable
    private fun Sample(selectedIndex: Int) {
        ChoicePreference(
            title = "Theme",
            options = options,
            optionLabels = options,
            selectedOption = options[selectedIndex],
            onOptionSelected = {},
        )
    }
}
