package ru.dmdp.tishina.feature.history.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.testing.composables.PreviewSheet

/**
 * Baseline for the "no measurements yet" hero panel (FR-9 fallback).
 *
 * Tests run under Robolectric Native graphics — the same setup as `:feature:measure`'s
 * screenshot suite. Pinned to a 360×720 mdpi profile so the CTA button sits at a
 * predictable Y coordinate across the suite.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h720dp-xhdpi")
class HistoryEmptyStateScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun empty_state_light() = capture(dark = false, name = "empty_state_light")

    @Test
    fun empty_state_dark() = capture(dark = true, name = "empty_state_dark")

    private fun capture(dark: Boolean, name: String) {
        composeTestRule.setContent {
            PreviewSheet(name = name, darkTheme = dark) {
                HistoryEmptyState(onNavigateToMeasure = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage(
            "src/test/snapshots/HistoryEmptyStateScreenshotTest_$name.png",
        )
    }
}
