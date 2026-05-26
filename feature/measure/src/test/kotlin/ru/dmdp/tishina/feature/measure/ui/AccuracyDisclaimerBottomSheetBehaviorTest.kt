package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.feature.measure.R

/**
 * Drives the FR-22 accuracy disclaimer sheet content. Renders the Window-less
 * [AccuracyDisclaimerSheetContent] — same trade-off as MeasureSaveDialogValidationTest
 * (Modal sub-Windows don't settle under Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Pin the locale to en-US so the hardcoded title assertion at the end of the first test
// (`assertEquals("About measurement accuracy", expectedTitle)`) doesn't break the day a
// future contributor adds `qualifiers=ru` to `robolectric.properties` for Russian rendering
// coverage. Width/height/density qualifiers are kept as-is.
@Config(qualifiers = "en-rUS-w360dp-h640dp-xhdpi")
class AccuracyDisclaimerBottomSheetBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun disclaimer_title_and_body_use_spec_wording() {
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AccuracyDisclaimerSheetContent(onShowFullDisclaimer = {})
                }
            }
        }

        val resources = ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .resources
        val expectedTitle = resources.getString(R.string.measure_disclaimer_title)
        val expectedBody = resources.getString(R.string.measure_disclaimer_body)

        // Spec § 11 anchors — these phrases are the contract for the FR-22 short blurb. If
        // marketing rewords the strings without these anchors the user loses the regulatory
        // safety message.
        assertTrue(
            "Body must reference MEMS microphones (spec § 11)",
            expectedBody.contains("MEMS", ignoreCase = true),
        )
        assertTrue(
            "Body must reference ±3–5 dB(A) (spec § 11)",
            expectedBody.contains("±3–5"),
        )
        assertTrue(
            "Body must clarify the meter is not certified for official measurements (spec § 11)",
            expectedBody.contains("IEC 61672", ignoreCase = true) ||
                expectedBody.contains("certified", ignoreCase = true) ||
                expectedBody.contains("сертифицировано", ignoreCase = true),
        )

        composeTestRule
            .onNodeWithTag(AccuracyDisclaimerSheetTitleTestTag)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(AccuracyDisclaimerSheetBodyTestTag)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(AccuracyDisclaimerSheetMoreTestTag)
            .assertIsDisplayed()

        // Sanity: the title resource really is what we render.
        assertEquals("About measurement accuracy", expectedTitle)
    }

    @Test
    fun clicking_learn_more_invokes_callback() {
        var moreClicks = 0
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AccuracyDisclaimerSheetContent(onShowFullDisclaimer = { moreClicks += 1 })
                }
            }
        }

        composeTestRule.onNodeWithTag(AccuracyDisclaimerSheetMoreTestTag).performClick()
        composeTestRule.waitForIdle()

        assertEquals("Learn more click should fire onShowFullDisclaimer exactly once", 1, moreClicks)
    }
}
