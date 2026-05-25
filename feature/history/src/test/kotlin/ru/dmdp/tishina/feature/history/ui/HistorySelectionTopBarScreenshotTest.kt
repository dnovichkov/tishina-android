package ru.dmdp.tishina.feature.history.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
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

/**
 * Locks the FR-12 multi-select TopBar permutations:
 *
 *  - selectedCount=1, 3, 5(all) variants exercise the russian plural ladder
 *    (one / few / many — see strings.xml plurals).
 *  - selectedCount=0 captures the "ClearSelection just fired" intermediate state
 *    where the user is in selection mode but nothing is checked; the Delete button
 *    must be disabled and the SelectAll affordance must still be visible.
 *
 * The toggle between SelectAll and ClearSelection in the actions row is driven by
 * `selectedCount == totalCount` — when everything is selected we show the inverse
 * action so the user has a single-tap escape from "all checked".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class HistorySelectionTopBarScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selection_topbar_1_selected_light() = capture(
        dark = false,
        name = "selection_topbar_1_selected_light",
        selectedCount = 1,
        totalCount = 5,
    )

    @Test
    fun selection_topbar_1_selected_dark() = capture(
        dark = true,
        name = "selection_topbar_1_selected_dark",
        selectedCount = 1,
        totalCount = 5,
    )

    @Test
    fun selection_topbar_3_selected_light() = capture(
        dark = false,
        name = "selection_topbar_3_selected_light",
        selectedCount = 3,
        totalCount = 5,
    )

    @Test
    fun selection_topbar_3_selected_dark() = capture(
        dark = true,
        name = "selection_topbar_3_selected_dark",
        selectedCount = 3,
        totalCount = 5,
    )

    @Test
    fun selection_topbar_all_5_selected_light() = capture(
        dark = false,
        name = "selection_topbar_all_5_selected_light",
        selectedCount = 5,
        totalCount = 5,
    )

    @Test
    fun selection_topbar_all_5_selected_dark() = capture(
        dark = true,
        name = "selection_topbar_all_5_selected_dark",
        selectedCount = 5,
        totalCount = 5,
    )

    @Test
    fun selection_topbar_0_selected_light() = capture(
        dark = false,
        name = "selection_topbar_0_selected_light",
        selectedCount = 0,
        totalCount = 5,
    )

    @Test
    fun selection_topbar_0_selected_dark() = capture(
        dark = true,
        name = "selection_topbar_0_selected_dark",
        selectedCount = 0,
        totalCount = 5,
    )

    private fun capture(
        dark: Boolean,
        name: String,
        selectedCount: Int,
        totalCount: Int,
    ) {
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                HistorySelectionTopBar(
                    selectedCount = selectedCount,
                    totalCount = totalCount,
                    onSelectAll = {},
                    onClearSelection = {},
                    onCancel = {},
                    onDelete = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        composeTestRule.onRoot().captureSnapshot("HistorySelectionTopBarScreenshotTest_$name")
    }
}
