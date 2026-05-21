package ru.dmdp.tishina.core.ui.preferences

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.testing.composables.PreviewSheet
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

/**
 * Baseline coverage for [PreferenceCategory] — verifies the category header
 * renders above its slot content and follows the project palette in light + dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PreferenceCategoryScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun preference_category_light() {
        composeTestRule.setContent {
            PreviewSheet(name = "preference_category_light") {
                Sample()
            }
        }
        composeTestRule.onRoot().captureSnapshot("PreferenceCategoryScreenshotTest_preference_category_light")
    }

    @Test
    fun preference_category_dark() {
        composeTestRule.setContent {
            PreviewSheet(name = "preference_category_dark", darkTheme = true) {
                Sample()
            }
        }
        composeTestRule.onRoot().captureSnapshot("PreferenceCategoryScreenshotTest_preference_category_dark")
    }
}

@Composable
private fun Sample() {
    PreferenceCategory(title = "Measurement") {
        SwitchPreference(
            title = "Item one",
            subtitle = "Subtitle one",
            checked = true,
            onCheckedChange = {},
        )
        Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
        SwitchPreference(
            title = "Item two",
            subtitle = "Subtitle two",
            checked = false,
            onCheckedChange = {},
        )
        Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
        SwitchPreference(
            title = "Item three",
            checked = true,
            onCheckedChange = {},
        )
    }
}
