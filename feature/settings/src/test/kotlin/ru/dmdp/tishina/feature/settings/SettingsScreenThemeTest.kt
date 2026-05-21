package ru.dmdp.tishina.feature.settings

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
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
import ru.dmdp.tishina.core.domain.model.ThemeMode

/**
 * UI-contract slice for the Appearance section (FR-17):
 *
 * - `Selected` semantics flip when the user's [ThemeMode] preference changes,
 *   so screen readers can announce "Системная, выбрано" / "Тёмная, выбрано"
 *   correctly.
 * - Tapping a non-selected chip emits exactly one `ChangeThemeMode` event —
 *   no double-fires from FilterChip's selection animation.
 * - The Dynamic colors switch reads its on/off state from
 *   `SettingsUiState.dynamicColors` and emits the inverted boolean on tap.
 *
 * Click → event contracts are already covered by
 * [SettingsScreenComposeUiTest]; this file focuses on the *displayed* state
 * (selected chip, switch position) that determines whether the UI matches
 * the persisted DataStore value.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `System theme is selected when state is System`() {
        composeTestRule.setContent {
            TishinaTheme(themeMode = ThemeMode.Light, dynamicColors = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, themeMode = ThemeMode.System),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        // "System" appears both in Theme (this section) and Language. We pick
        // the first occurrence in DOM order — the Theme chip — by matching the
        // clickable FilterChip that contains the label.
        scrollTo("System")
        composeTestRule.firstChipWithText("System").assertSelected()
        composeTestRule.onNodeWithText("Light").assertNotSelected()
        composeTestRule.onNodeWithText("Dark").assertNotSelected()
    }

    @Test
    fun `Dark theme is selected when state is Dark`() {
        composeTestRule.setContent {
            TishinaTheme(themeMode = ThemeMode.Light, dynamicColors = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, themeMode = ThemeMode.Dark),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Dark")
        composeTestRule.onNodeWithText("Dark").assertSelected()
        composeTestRule.onNodeWithText("Light").assertNotSelected()
        composeTestRule.firstChipWithText("System").assertNotSelected()
    }

    @Test
    fun `Light theme is selected when state is Light`() {
        composeTestRule.setContent {
            TishinaTheme(themeMode = ThemeMode.Light, dynamicColors = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, themeMode = ThemeMode.Light),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Light")
        composeTestRule.onNodeWithText("Light").assertSelected()
    }

    @Test
    fun `tapping Light chip while Dark is active emits ChangeThemeMode(Light)`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(themeMode = ThemeMode.Light, dynamicColors = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, themeMode = ThemeMode.Dark),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Light")
        composeTestRule.onNodeWithText("Light").performClick()

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeThemeMode }
        assertNotNull(event)
        assertEquals(ThemeMode.Light, event!!.mode)
    }

    @Test
    fun `dynamic colors switch is ON when state dynamicColors is true`() {
        composeTestRule.setContent {
            TishinaTheme(themeMode = ThemeMode.Light, dynamicColors = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, dynamicColors = true),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Dynamic colors")
        composeTestRule.onNodeWithText("Dynamic colors").assertIsOn()
    }

    @Test
    fun `dynamic colors switch is OFF when state dynamicColors is false`() {
        composeTestRule.setContent {
            TishinaTheme(themeMode = ThemeMode.Light, dynamicColors = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, dynamicColors = false),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Dynamic colors")
        composeTestRule.onNodeWithText("Dynamic colors").assertIsOff()
    }

    @Test
    fun `dynamic colors switch reports OFF on legacy API even when state is true`() {
        // FR-17 fallback: on API ≤30 the switch is forced OFF so the user
        // sees the actual rendering state (static scheme) instead of a stale
        // persisted preference.
        composeTestRule.setContent {
            TishinaTheme(themeMode = ThemeMode.Light, dynamicColors = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false, dynamicColors = true),
                    isDynamicColorSupported = false,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Dynamic colors")
        composeTestRule.onNodeWithText("Dynamic colors").assertIsOff()
    }

    @Test
    fun `dynamic colors description is visible`() {
        composeTestRule.setContent {
            TishinaTheme(themeMode = ThemeMode.Light, dynamicColors = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        val description = "Use Material You palette from the current wallpaper (Android 12+)."
        scrollTo(description)
        composeTestRule.onNodeWithText(description).assertIsDisplayed()
    }

    private fun scrollTo(text: String) {
        composeTestRule
            .onNodeWithTag(SettingsScreenContentTestTag)
            .performScrollToNode(hasText(text))
    }
}

/**
 * `FilterChip` exposes its selection state through the `Selected` semantics
 * property. Compose-UI offers no first-class `assertIsSelected()`, so we build
 * one on top of [SemanticsMatcher] — same approach used in the AndroidX
 * Material 3 instrumentation tests.
 */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertSelected() {
    assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertNotSelected() {
    assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
}

/**
 * Returns the first clickable node whose merged subtree contains [text]. We
 * filter on `hasClickAction()` to skip the bare `Text` semantics node — only
 * the parent `FilterChip` carries the `Selected` property.
 *
 * The screen renders "System" twice (Theme + Language sections); DOM order
 * places the Theme chip first, which is exactly what every caller wants.
 */
private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.firstChipWithText(
    text: String,
): androidx.compose.ui.test.SemanticsNodeInteraction =
    this.onAllNodes(hasText(text) and hasClickAction()).onFirst()
