package ru.dmdp.tishina.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.ui.R

@RunWith(RobolectricTestRunner::class)
class PlaceholderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders title and description from string resources`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                PlaceholderScreen(
                    titleRes = R.string.measure_title,
                    descriptionRes = R.string.measure_placeholder,
                )
            }
        }

        composeTestRule.onNodeWithTag(PlaceholderScreenTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(PlaceholderTitleTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(PlaceholderDescriptionTestTag).assertIsDisplayed()
        // Default Robolectric locale is en — placeholder string is "Coming soon".
        composeTestRule.onNodeWithText("Coming soon").assertIsDisplayed()
    }

    @Test
    fun `renders different titles for different screens`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                PlaceholderScreen(
                    titleRes = R.string.history_title,
                    descriptionRes = R.string.history_placeholder,
                )
            }
        }

        composeTestRule.onNodeWithText("History").assertIsDisplayed()
    }
}
