package ru.dmdp.tishina

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.dmdp.tishina.feature.history.HistoryScreenTestTag
import ru.dmdp.tishina.feature.measure.MeasureScreenTestTag
import ru.dmdp.tishina.navigation.TopLevelDestination
import ru.dmdp.tishina.ui.TishinaNavigationBarTestTag
import ru.dmdp.tishina.ui.navigationItemTestTag

/**
 * Phase 6 Task 9 — end-to-end smoke flow on a real Android emulator (CI matrix
 * API 26 / 30 / 34 via `reactivecircus/android-emulator-runner@v2`).
 *
 * Robolectric (used in `:app/src/test/`) covers the Compose-side logic, but it
 * cannot exercise: real `RECORD_AUDIO` permission dialogs, the Storage Access
 * Framework picker (system UI), or APK-installed ProGuard/R8-shrunk classes.
 * This instrumentation test catches regressions that only appear on a real
 * device — Hilt graph wiring, `combinedClickable` semantics on the actual
 * platform, navigation-rail vs. bottom-bar selection on the test emulator's
 * window size class.
 *
 * **Scope is deliberately a single happy-path flow.** Granular UI behavior is
 * already covered by Robolectric tests; this test exists to confirm that the
 * whole stack boots on each supported API level. Adding more scenarios here
 * would multiply CI minutes (each emulator takes ~3 min to start) without
 * proportionate signal.
 *
 * **Permission grants:** Android 6+ runtime permissions are pre-granted by
 * `androidx.test.runner.AndroidJUnitRunner` via `am instrument -e
 * grantRuntimePermissions true` (default-on since AndroidX Test 1.4). The
 * permission rationale sheet that `MeasureViewModel` shows on first launch is
 * therefore skipped by the runner for `RECORD_AUDIO`.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class SmokeFlowInstrumentationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    /**
     * Verifies the bottom-bar navigation flow (Measure → History → back) and that
     * the chrome around each route renders without a crash. We do **not** exercise
     * FAB / Save / Export here — those touch system UI (permission sheet on first
     * launch, SAF picker, share-chooser) which interacts with device-specific OEM
     * dialogs and is the leading source of flakiness on reactivecircus emulators
     * across API levels. We test the surfaces that the user reaches between those
     * system gates instead.
     *
     * History (not About) is the click target because the CI emulator profile
     * `pixel_6` resolves to `WindowWidthSizeClass.Compact`, where About lives only
     * inside Settings's footer link — and the previous version of this test tried
     * to click `TishinaAboutActionTestTag`, which on Compact widths actually tags
     * the `?` disclaimer icon (a sheet trigger, NOT a navigation control). The
     * tag-collision has been fixed (the disclaimer icon now uses a separate tag);
     * driving the bottom-nav `History` item is a stable target across all widths.
     */
    @Test
    fun smoke_navigateAcrossPrimaryDestinations() {
        // Ensure the device is awake; on cold-boot emulators the activity is
        // launched before the lockscreen has dismissed itself.
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
        hiltRule.inject()

        // Wait for MeasureScreen — the default destination — to compose. The
        // app boots into Measure (see `TishinaDestinations`); permission is
        // grant-on-launch through `AndroidJUnitRunner`.
        composeRule.waitUntil(timeoutMillis = SCREEN_TIMEOUT_MS) {
            composeRule
                .onAllNodes(hasTestTag(MeasureScreenTestTag))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(MeasureScreenTestTag).assertIsDisplayed()
        composeRule.onNodeWithTag(TishinaNavigationBarTestTag).assertIsDisplayed()

        // Tap the bottom-nav History item. This is a top-level destination that
        // exists on every supported window-size class, so the test is stable
        // regardless of emulator profile.
        composeRule
            .onNodeWithTag(navigationItemTestTag(TopLevelDestination.History))
            .performClick()
        composeRule.waitForIdle()

        // History route reachable: assert by tag rather than text because the
        // emulator locale may differ from the test JVM default.
        composeRule.waitUntil(timeoutMillis = SCREEN_TIMEOUT_MS) {
            composeRule
                .onAllNodes(hasTestTag(HistoryScreenTestTag))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(HistoryScreenTestTag).assertIsDisplayed()

        // Back to default destination — using device back so the
        // NavController's pop-handling is exercised on the real platform.
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitUntil(timeoutMillis = SCREEN_TIMEOUT_MS) {
            composeRule
                .onAllNodes(hasTestTag(MeasureScreenTestTag))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(MeasureScreenTestTag).assertIsDisplayed()
    }

    private companion object {
        private const val SCREEN_TIMEOUT_MS = 10_000L
    }
}
