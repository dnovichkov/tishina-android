package ru.dmdp.tishina

import android.os.Build
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.testutils.MeasureScreenTestStub
import ru.dmdp.tishina.ui.TishinaApp
import ru.dmdp.tishina.ui.TishinaNavigationBarTestTag
import ru.dmdp.tishina.ui.TishinaNavigationRailTestTag

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class AdaptiveNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `compact width shows bottom NavigationBar`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
                TishinaApp(windowSizeClass = sizeClass, measureContent = { MeasureScreenTestStub() })
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TishinaNavigationBarTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TishinaNavigationRailTestTag).assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `medium width shows NavigationRail`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(720.dp, 1024.dp))
                TishinaApp(windowSizeClass = sizeClass, measureContent = { MeasureScreenTestStub() })
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TishinaNavigationRailTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TishinaNavigationBarTestTag).assertDoesNotExist()
    }

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `expanded width shows NavigationRail`() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(960.dp, 1024.dp))
                TishinaApp(windowSizeClass = sizeClass, measureContent = { MeasureScreenTestStub() })
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TishinaNavigationRailTestTag).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TishinaNavigationBarTestTag).assertDoesNotExist()
    }
}
