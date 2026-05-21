package ru.dmdp.tishina.feature.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
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
import ru.dmdp.tishina.core.domain.model.AppLocale

/**
 * FR-18 UI contract for the Language section:
 *
 *  - The selected `AppLocale` is announced via `FilterChip`'s `Selected`
 *    semantics so screen readers can say "Russian, selected" / "Английский,
 *    выбрано".
 *  - Tapping a non-selected chip emits exactly one `ChangeAppLocale` event
 *    with the matching enum — the ViewModel persists *and* fires the
 *    `ApplyAppLocale` effect that `MainActivity` translates into an
 *    `AppCompatDelegate` call (covered by [MainActivityLocaleEffectTest]).
 *
 * The word "System" appears in two chip groups (Theme + Language). The
 * Language chip is rendered second in DOM order, so this file relies on
 * [lastChipWithText] — mirroring the inverse helper in
 * [SettingsScreenThemeTest] that picks the first chip for Theme.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenLanguageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `System locale is selected when state is System`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, locale = AppLocale.System),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Application language")
        composeTestRule.lastChipWithText("System").assertSelected()
        composeTestRule.onNodeWithText("Russian").assertNotSelected()
        composeTestRule.onNodeWithText("English").assertNotSelected()
    }

    @Test
    fun `Russian locale is selected when state is Russian`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, locale = AppLocale.Russian),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Russian")
        composeTestRule.onNodeWithText("Russian").assertSelected()
        composeTestRule.onNodeWithText("English").assertNotSelected()
        composeTestRule.lastChipWithText("System").assertNotSelected()
    }

    @Test
    fun `English locale is selected when state is English`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, locale = AppLocale.English),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("English")
        composeTestRule.onNodeWithText("English").assertSelected()
    }

    @Test
    fun `tapping Russian chip while System is active emits ChangeAppLocale(Russian)`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, locale = AppLocale.System),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Russian")
        composeTestRule.onNodeWithText("Russian").performClick()

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeAppLocale }
        assertNotNull(event)
        assertEquals(AppLocale.Russian, event!!.locale)
    }

    @Test
    fun `tapping English chip while Russian is active emits ChangeAppLocale(English)`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, locale = AppLocale.Russian),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("English")
        composeTestRule.onNodeWithText("English").performClick()

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeAppLocale }
        assertNotNull(event)
        assertEquals(AppLocale.English, event!!.locale)
    }

    @Test
    fun `tapping System chip while English is active emits ChangeAppLocale(System)`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, locale = AppLocale.English),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Application language")
        composeTestRule.lastChipWithText("System").performClick()

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeAppLocale }
        assertNotNull(event)
        assertEquals(AppLocale.System, event!!.locale)
    }

    private fun scrollTo(text: String) {
        composeTestRule
            .onNodeWithTag(SettingsScreenContentTestTag)
            .performScrollToNode(hasText(text))
    }
}

/**
 * The Theme section also exposes a "System" chip — its node is rendered first
 * in DOM order. The Language chip is the last clickable node with that label.
 * Picking [onLast] guarantees we target the Language chip without depending on
 * test tags inside the shared `ChoicePreference` composable.
 */
private fun ComposeContentTestRule.lastChipWithText(text: String): SemanticsNodeInteraction =
    this.onAllNodes(hasText(text) and hasClickAction()).onLast()

@Suppress("UnusedPrivateMember")
private fun ComposeContentTestRule.firstChipWithText(text: String): SemanticsNodeInteraction =
    this.onAllNodes(hasText(text) and hasClickAction()).onFirst()

private fun SemanticsNodeInteraction.assertSelected() {
    assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
}

private fun SemanticsNodeInteraction.assertNotSelected() {
    assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
}
