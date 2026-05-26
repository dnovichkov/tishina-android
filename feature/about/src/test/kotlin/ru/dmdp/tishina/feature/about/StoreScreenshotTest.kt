package ru.dmdp.tishina.feature.about

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
import ru.dmdp.tishina.core.domain.model.OssLicense
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Store-card screenshot for the About screen at 1080 × 1920 px. Captured in
 * Russian (class default) and English (method-level @Config override). Version
 * is pinned to the v1.0.0 marketing tag so the catalogue copy stays stable even
 * if local dev builds carry a `-dev` suffix.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-rRU-w360dp-h640dp-xxhdpi")
class StoreScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun about_store_default_light_ru() = capture("about_store_default_light_ru")

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp-xxhdpi")
    fun about_store_default_light_en() = capture("about_store_default_light_en")

    private fun capture(name: String) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(
                        version = AppVersion("1.0.0", 1),
                        ossLicenses = listOf(
                            OssLicense("Kotlin", "2.0.21", "Apache-2.0", "https://kotlinlang.org"),
                            OssLicense("Hilt", "2.55", "Apache-2.0", "https://dagger.dev/hilt/"),
                            OssLicense(
                                name = "Room",
                                version = "2.8.4",
                                license = "Apache-2.0",
                                url = "https://developer.android.com/training/data-storage/room",
                            ),
                        ),
                        loading = false,
                    ),
                    onNavigateBack = {},
                    onOpenUrl = {},
                )
            }
        }
        composeTestRule.onRoot().captureSnapshot("StoreScreenshotTest_$name")
    }
}
