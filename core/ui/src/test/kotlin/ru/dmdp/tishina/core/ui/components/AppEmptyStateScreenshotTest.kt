package ru.dmdp.tishina.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AppEmptyStateScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun empty_state_light() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                EmptyStateHost()
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/AppEmptyStateScreenshotTest_empty_state_light.png",
        )
    }

    @Test
    fun empty_state_dark() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = true, dynamicColor = false) {
                EmptyStateHost()
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/AppEmptyStateScreenshotTest_empty_state_dark.png",
        )
    }
}

@Composable
private fun EmptyStateHost() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AppEmptyState(
            icon = Icons.Outlined.History,
            title = "Нет записей",
            description = "Сделайте первое измерение, чтобы увидеть его в истории.",
            ctaLabel = "Начать измерение",
            onCtaClick = {},
        )
    }
}
