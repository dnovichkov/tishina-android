package ru.dmdp.tishina.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * UI contract for the Fast / Slow time weighting chip group (FR-16).
 *
 * Why not testing the FilterChip selected-color directly: under Robolectric the
 * filter-chip selected state is exposed as a [SemanticsProperties.Selected]
 * boolean — that's what `assertIsSelected` reads. We pair the selection check
 * with an event-emission check on the *other* chip, which together prove the
 * row is honouring the [SettingsUiState.timeWeighting] input.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTimeWeightingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `initial Fast weighting marks Fast chip as selected`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, timeWeighting = TimeWeighting.FAST),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Fast (125 ms)")
        composeTestRule.onNodeWithText("Fast (125 ms)").assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun `initial Slow weighting marks Slow chip as selected`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, timeWeighting = TimeWeighting.SLOW),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Slow (1 s)")
        composeTestRule.onNodeWithText("Slow (1 s)").assertIsSelected()
    }

    @Test
    fun `click on Slow chip emits ChangeTimeWeighting SLOW`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, timeWeighting = TimeWeighting.FAST),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Slow (1 s)")
        composeTestRule.onNodeWithText("Slow (1 s)").performClick()

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeTimeWeighting }
        assertNotNull(event)
        assertEquals(TimeWeighting.SLOW, event!!.weighting)
    }

    @Test
    fun `click on Fast chip while Slow is selected emits ChangeTimeWeighting FAST`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, timeWeighting = TimeWeighting.SLOW),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Fast (125 ms)")
        composeTestRule.onNodeWithText("Fast (125 ms)").performClick()

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeTimeWeighting }
        assertNotNull(event)
        assertEquals(TimeWeighting.FAST, event!!.weighting)
    }

    @Test
    fun `time weighting description is visible`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Fast — for dynamic sounds; Slow — for averaged levels.")
        composeTestRule.onNodeWithText(
            "Fast — for dynamic sounds; Slow — for averaged levels.",
        ).assertIsDisplayed()
    }

    private fun scrollTo(text: String) {
        composeTestRule
            .onNodeWithTag(SettingsScreenContentTestTag)
            .performScrollToNode(hasText(text))
    }
}
