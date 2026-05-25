package ru.dmdp.tishina.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
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
 * Locks the russian plural ladder on the confirm dialog title:
 *
 *  - count=1 → "Удалить 1 замер?" (one)
 *  - count=3 → "Удалить 3 замера?" (few)
 *  - count=5 → "Удалить 5 замеров?" (many)
 *
 * Same Surface-only baseline strategy used in MeasureSaveDialogScreenshotTest —
 * the production [BulkDeleteConfirmDialog] wraps an AlertDialog whose sub-Window
 * never settles under Robolectric's idle window. The visuals we care about are
 * inside [BulkDeleteConfirmDialogContent] (a Surface), so we snapshot that.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-rRU-w360dp-h640dp-xhdpi")
class BulkDeleteConfirmDialogScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun confirm_1_item_ru() = capture(count = 1, dark = false, name = "confirm_1_item_ru")

    @Test
    fun confirm_3_items_ru() = capture(count = 3, dark = false, name = "confirm_3_items_ru")

    @Test
    fun confirm_5_items_ru() = capture(count = 5, dark = false, name = "confirm_5_items_ru")

    @Test
    fun confirm_3_items_dark() = capture(count = 3, dark = true, name = "confirm_3_items_dark")

    private fun capture(count: Int, dark: Boolean, name: String) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                TishinaTheme(darkTheme = dark, dynamicColor = false) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            BulkDeleteConfirmDialogContent(
                                count = count,
                                onConfirm = {},
                                onDismiss = {},
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureSnapshot("BulkDeleteConfirmDialogScreenshotTest_$name")
    }
}
