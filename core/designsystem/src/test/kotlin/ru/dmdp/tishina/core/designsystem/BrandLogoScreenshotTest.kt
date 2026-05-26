package ru.dmdp.tishina.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Phase 6 Task 1 — visual regression guard for the brand mark vector that lives in
 * `:core:designsystem/res/drawable/ic_brand_logo.xml`.
 *
 * The launcher foreground is verified manually in Android Studio's Asset Studio
 * (which has the canonical adaptive-icon mask preview); these snapshots cover the
 * in-app usage instead — i.e. how the same mark renders inside a tinted Surface card
 * at the size the AboutScreen header uses (48 dp icon inside a 72 dp container).
 *
 * Two baselines (light + dark) keep the test sensitive to theme tint changes without
 * exploding into a per-density matrix — vector drawables already scale density-free.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w160dp-h160dp-xhdpi")
class BrandLogoScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun brand_logo_in_primary_container_light() = capture(dark = false, name = "brand_logo_in_primary_container_light")

    @Test
    fun brand_logo_in_primary_container_dark() = capture(dark = true, name = "brand_logo_in_primary_container_dark")

    private fun capture(dark: Boolean, name: String) {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = dark, dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    BrandTile()
                }
            }
        }
        composeTestRule.onRoot().captureSnapshot("BrandLogoScreenshotTest_$name")
    }

    /**
     * Mirrors the AboutScreen `AboutHeader` chrome so a regression in either the vector
     * geometry OR in the surrounding tint chain is caught here, in the design system
     * module, rather than only by the downstream feature test.
     */
    @Composable
    private fun BrandTile() {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_brand_logo),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
    }
}
