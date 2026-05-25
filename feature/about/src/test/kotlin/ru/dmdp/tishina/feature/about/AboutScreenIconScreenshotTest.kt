package ru.dmdp.tishina.feature.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.AppVersion
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Phase 6 Task 1 — pixel-level regression guard for the real brand mark wired into
 * the AboutScreen header.
 *
 * Why a focused test (and not just re-record AboutScreenScreenshotTest)?
 * The full-screen baselines also drift when *unrelated* parts of the screen change
 * (a translation tweak, a `Spacer` resize). A header-only snapshot fails ONLY when
 * the brand mark itself regresses (vector path edit, tint swap, sizing change),
 * which makes the signal in PR reviews much clearer.
 *
 * `AboutHeader` is `private` in [AboutScreen]; we rebuild a minimal harness here so
 * the test target doesn't leak visibility just for screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h360dp-xhdpi")
class AboutScreenIconScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun about_header_with_real_icon_light() = capture(dark = false, name = "about_header_with_real_icon_light")

    @Test
    fun about_header_with_real_icon_dark() = capture(dark = true, name = "about_header_with_real_icon_dark")

    private fun capture(dark: Boolean, name: String) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    HeaderHost(version = AppVersion("1.0.0", 1))
                }
            }
        }
        composeTestRule.onRoot().captureSnapshot("AboutScreenIconScreenshotTest_$name")
    }

    /**
     * Renders the full AboutScreen header by reusing the production composable through
     * [AboutScreenContent]. The 360dp-tall viewport clips lower sections so the captured
     * frame is dominated by the header (icon + title + subtitle + version label), all of
     * which live in the `item("header")` LazyColumn slot at the top.
     */
    @Composable
    private fun HeaderHost(version: AppVersion) {
        AboutScreenContent(
            state = AboutUiState(
                version = version,
                ossLicenses = emptyList(),
                loading = false,
            ),
            onNavigateBack = {},
            onOpenUrl = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
