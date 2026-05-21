package ru.dmdp.tishina.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting

/**
 * Compose-UI contract for [SettingsScreenContent]: navigation back button fires,
 * the loading indicator gates the controls, every category renders its header,
 * and chip / switch taps emit the matching [SettingsUiEvent].
 *
 * The LazyColumn host pushes most controls off-screen at the default Robolectric
 * viewport, so every interaction below "Measurement" first scrolls the node into
 * view via [performScrollToNode] — this mirrors how the real user finds those
 * preferences.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `loading state renders progress indicator instead of controls`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = true),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(SettingsScreenLoadingTestTag).assertIsDisplayed()
    }

    @Test
    fun `loaded state renders all category headers`() {
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

        // Default Robolectric locale is en — values from values/strings.xml.
        composeTestRule.onNodeWithText("Measurement").assertIsDisplayed()
        scrollTo("Appearance")
        composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
        scrollTo("Application language")
        composeTestRule.onNodeWithText("Application language").assertIsDisplayed()
        scrollTo("About the app")
        composeTestRule.onNodeWithText("About the app").assertIsDisplayed()
    }

    @Test
    fun `back navigation icon invokes callback`() {
        var backCount = 0
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = { backCount++ },
                    onAboutClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back to Measure").performClick()
        assertEquals(1, backCount)
    }

    @Test
    fun `about footer link invokes callback`() {
        var aboutCount = 0
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(loading = false),
                    isDynamicColorSupported = true,
                    onEvent = {},
                    onNavigateBack = {},
                    onAboutClick = { aboutCount++ },
                )
            }
        }

        scrollTo("About the app")
        composeTestRule.onNodeWithText("About the app").performClick()
        assertEquals(1, aboutCount)
    }

    @Test
    fun `theme chip click emits ChangeThemeMode event`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        loading = false,
                        themeMode = ThemeMode.System,
                    ),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Dark")
        composeTestRule.onNodeWithText("Dark").performClick()

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeThemeMode }
        assertNotNull(event)
        assertEquals(ThemeMode.Dark, event!!.mode)
    }

    @Test
    fun `time weighting chip click emits ChangeTimeWeighting event`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        loading = false,
                        timeWeighting = TimeWeighting.FAST,
                    ),
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
    fun `locale chip click emits ChangeAppLocale event`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        loading = false,
                        locale = AppLocale.System,
                    ),
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
    fun `dynamic colors switch toggles emit ChangeDynamicColors event`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        loading = false,
                        dynamicColors = true,
                    ),
                    isDynamicColorSupported = true,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Dynamic colors")
        composeTestRule.onNodeWithText("Dynamic colors").performClick()

        val event = events.firstNotNullOfOrNull { it as? SettingsUiEvent.ChangeDynamicColors }
        assertNotNull(event)
        assertEquals(false, event!!.enabled)
    }

    @Test
    fun `dynamic colors disabled on legacy API does not emit events`() {
        val events = mutableListOf<SettingsUiEvent>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                SettingsScreenContent(
                    state = SettingsUiState(
                        loading = false,
                        dynamicColors = true,
                    ),
                    isDynamicColorSupported = false,
                    onEvent = events::add,
                    onNavigateBack = {},
                    onAboutClick = {},
                )
            }
        }

        scrollTo("Dynamic colors")
        composeTestRule.onNodeWithText("Dynamic colors").performClick()
        assertTrue(events.none { it is SettingsUiEvent.ChangeDynamicColors })
    }

    private fun scrollTo(text: String) {
        composeTestRule
            .onNodeWithTag(SettingsScreenContentTestTag)
            .performScrollToNode(hasText(text))
    }
}
