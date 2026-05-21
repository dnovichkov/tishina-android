package ru.dmdp.tishina.core.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.testing.composables.PreviewSheet
import ru.dmdp.tishina.core.testing.rules.captureSnapshot

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SwitchPreferenceScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun switch_on_light() = capture(checked = true, dark = false, name = "switch_on_light")

    @Test
    fun switch_on_dark() = capture(checked = true, dark = true, name = "switch_on_dark")

    @Test
    fun switch_off_light() = capture(checked = false, dark = false, name = "switch_off_light")

    @Test
    fun switch_off_dark() = capture(checked = false, dark = true, name = "switch_off_dark")

    private fun capture(checked: Boolean, dark: Boolean, name: String) {
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                Sample(checked)
            }
        }
        composeTestRule.onRoot().captureSnapshot("SwitchPreferenceScreenshotTest_$name")
    }

    @Composable
    private fun Sample(checked: Boolean) {
        SwitchPreference(
            title = "Dynamic colors",
            subtitle = "Use Material You palette from the current wallpaper.",
            checked = checked,
            onCheckedChange = {},
        )
    }
}
