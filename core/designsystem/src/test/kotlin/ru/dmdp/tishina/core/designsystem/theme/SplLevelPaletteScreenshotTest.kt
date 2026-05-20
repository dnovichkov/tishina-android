package ru.dmdp.tishina.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Visual regression test for the SPL level palette. We snapshot the 6-color
 * strip in both light and dark themes to catch accidental contrast/order
 * regressions (spec § 6: ≤40 / 41-60 / 61-75 / 76-85 / 86-100 / >100).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h160dp-xhdpi")
class SplLevelPaletteScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun palette_strip_light() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                PaletteHost()
            }
        }
        composeTestRule.onRoot().captureSnapshot("SplLevelPaletteScreenshotTest_palette_strip_light")
    }

    @Test
    fun palette_strip_dark() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = true, dynamicColor = false) {
                PaletteHost()
            }
        }
        composeTestRule.onRoot().captureSnapshot("SplLevelPaletteScreenshotTest_palette_strip_dark")
    }
}

@androidx.compose.runtime.Composable
private fun PaletteHost() {
    val palette = LocalSplLevelPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        CompositionLocalProvider(LocalSplLevelPalette provides palette) {
            SplPaletteStrip(palette)
        }
    }
}
