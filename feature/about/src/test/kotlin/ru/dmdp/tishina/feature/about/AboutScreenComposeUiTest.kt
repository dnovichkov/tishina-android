package ru.dmdp.tishina.feature.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.AppVersion
import ru.dmdp.tishina.core.domain.model.OssLicense

/**
 * Compose-UI contract for [AboutScreenContent] (FR-21):
 *  - loading gate renders progress indicator;
 *  - loaded state surfaces version, disclaimer body anchor («MEMS» substring), and link rows;
 *  - back button invokes the navigation callback;
 *  - link rows propagate URL clicks to `onOpenUrl` so the production composable can fire
 *    the `Intent.ACTION_VIEW` itself (kept out of the pure body for easier testing).
 */
// Pin the locale to en-US so hardcoded EN strings ("Back", "No licenses") used in the
// content-description lookups below remain valid. Without this pin, a future change to
// the module's `robolectric.properties` (or an inherited default change in Robolectric)
// flipping the locale to `ru` would silently break these tests.
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(qualifiers = "en-rUS")
class AboutScreenComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleVersion = AppVersion("0.5.0-polish", 42)
    private val sampleLicenses = listOf(
        OssLicense("Kotlin", "2.0.21", "Apache-2.0", "https://kotlinlang.org"),
        OssLicense("Hilt", "2.55", "Apache-2.0", "https://dagger.dev/hilt/"),
    )

    @Test
    fun `loading state renders progress indicator`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(loading = true),
                    onNavigateBack = {},
                    onOpenUrl = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(AboutScreenLoadingTestTag).assertIsDisplayed()
    }

    @Test
    fun `loaded state renders version label`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(version = sampleVersion, loading = false),
                    onNavigateBack = {},
                    onOpenUrl = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(AboutScreenVersionTestTag)
            .assertIsDisplayed()
    }

    @Test
    fun `disclaimer body keeps the MEMS regression anchor`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(version = sampleVersion, loading = false),
                    onNavigateBack = {},
                    onOpenUrl = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(AboutScreenContentTestTag)
            .performScrollToNode(hasText("MEMS", substring = true))
        composeTestRule
            .onNode(hasText("MEMS", substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun `back button invokes navigation callback`() {
        var backCount = 0
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(version = sampleVersion, loading = false),
                    onNavigateBack = { backCount++ },
                    onOpenUrl = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backCount)
    }

    @Test
    fun `clicking GitHub link forwards URL to onOpenUrl`() {
        val opened = mutableListOf<String>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(version = sampleVersion, loading = false),
                    onNavigateBack = {},
                    onOpenUrl = { opened += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(AboutScreenContentTestTag)
            .performScrollToNode(androidx.compose.ui.test.hasTestTag(AboutScreenGithubTestTag))
        composeTestRule.onNodeWithTag(AboutScreenGithubTestTag).performClick()

        assertEquals(1, opened.size)
        assertTrue(opened.first().startsWith("https://github.com/"))
    }

    @Test
    fun `clicking Privacy link forwards URL to onOpenUrl`() {
        val opened = mutableListOf<String>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(version = sampleVersion, loading = false),
                    onNavigateBack = {},
                    onOpenUrl = { opened += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(AboutScreenContentTestTag)
            .performScrollToNode(androidx.compose.ui.test.hasTestTag(AboutScreenPrivacyTestTag))
        composeTestRule.onNodeWithTag(AboutScreenPrivacyTestTag).performClick()

        // Compare against the exact resource value rather than a substring — a typo or
        // swap with `about_github_url` would slip through a `contains("privacy")` check
        // because both happen to be web URLs.
        val expectedUrl = ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .getString(ru.dmdp.tishina.feature.about.R.string.about_privacy_url)
        assertEquals(1, opened.size)
        assertNotNull(opened.first())
        assertEquals(expectedUrl, opened.first())
    }

    @Test
    fun `license rows render and forward URL on tap`() {
        val opened = mutableListOf<String>()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(
                        version = sampleVersion,
                        ossLicenses = sampleLicenses,
                        loading = false,
                    ),
                    onNavigateBack = {},
                    onOpenUrl = { opened += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(AboutScreenContentTestTag)
            .performScrollToNode(hasText("Kotlin"))
        composeTestRule.onNodeWithText("Kotlin").performClick()

        assertEquals(1, opened.size)
        assertEquals("https://kotlinlang.org", opened.first())
    }

    @Test
    fun `empty license list shows the empty-state copy`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(version = sampleVersion, ossLicenses = emptyList(), loading = false),
                    onNavigateBack = {},
                    onOpenUrl = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(AboutScreenContentTestTag)
            .performScrollToNode(hasText("No licenses", substring = true))
        composeTestRule
            .onNode(hasText("No licenses", substring = true))
            .assertIsDisplayed()
    }
}
