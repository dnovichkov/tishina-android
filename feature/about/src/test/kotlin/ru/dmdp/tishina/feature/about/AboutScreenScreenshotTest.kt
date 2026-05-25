package ru.dmdp.tishina.feature.about

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.AppVersion
import ru.dmdp.tishina.core.domain.model.OssLicense
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Roborazzi baselines for AboutScreen (FR-21):
 *  - `about_default_light/dark` — full screen with loaded version + licenses (top of scroll).
 *  - `about_empty_licenses_light/dark` — empty-state placeholder for the licenses block.
 *  - `about_font_scale_2x_light/dark` — NFR-14, the screen must remain readable at 200 % font.
 *
 * Each baseline targets [AboutScreenContent] directly (no Hilt / PackageManager) so the
 * captured pixels are deterministic across machines.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class AboutScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun about_default_light() = capture(state = defaultState(), dark = false, name = "about_default_light")

    @Test
    fun about_default_dark() = capture(state = defaultState(), dark = true, name = "about_default_dark")

    @Test
    fun about_empty_licenses_light() = capture(
        state = defaultState().copy(ossLicenses = emptyList()),
        dark = false,
        name = "about_empty_licenses_light",
    )

    @Test
    fun about_empty_licenses_dark() = capture(
        state = defaultState().copy(ossLicenses = emptyList()),
        dark = true,
        name = "about_empty_licenses_dark",
    )

    @Test
    fun about_font_scale_2x_light() = capture(
        state = defaultState(),
        dark = false,
        name = "about_font_scale_2x_light",
        fontScale = 2.0f,
    )

    @Test
    fun about_font_scale_2x_dark() = capture(
        state = defaultState(),
        dark = true,
        name = "about_font_scale_2x_dark",
        fontScale = 2.0f,
    )

    private fun capture(
        state: AboutUiState,
        dark: Boolean,
        name: String,
        fontScale: Float = 1.0f,
    ) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                if (fontScale == 1.0f) {
                    Host(state)
                } else {
                    val baseDensity = LocalDensity.current
                    val scaledDensity = Density(
                        density = baseDensity.density,
                        fontScale = fontScale,
                    )
                    CompositionLocalProvider(LocalDensity provides scaledDensity) {
                        Host(state)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureSnapshot("AboutScreenScreenshotTest_$name")
    }

    @Composable
    private fun Host(state: AboutUiState) {
        AboutScreenContent(
            state = state,
            onNavigateBack = {},
            onOpenUrl = {},
        )
    }

    private fun defaultState(): AboutUiState = AboutUiState(
        version = AppVersion("0.5.0-polish", 42),
        ossLicenses = listOf(
            OssLicense("Kotlin", "2.0.21", "Apache-2.0", "https://kotlinlang.org"),
            OssLicense("Hilt", "2.55", "Apache-2.0", "https://dagger.dev/hilt/"),
            OssLicense("Room", "2.8.4", "Apache-2.0", "https://developer.android.com/training/data-storage/room"),
        ),
        loading = false,
    )
}
