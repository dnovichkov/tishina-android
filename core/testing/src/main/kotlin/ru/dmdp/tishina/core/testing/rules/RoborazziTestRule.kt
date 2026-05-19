package ru.dmdp.tishina.core.testing.rules

import androidx.compose.ui.test.SemanticsNodeInteraction
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * Project-wide pixel tolerance for screenshot comparisons (1%).
 *
 * Tight enough to catch real regressions, loose enough that minor font-rendering
 * jitter across JDKs does not flake the suite.
 */
const val SnapshotChangeThreshold: Float = 0.01f

/**
 * Relative path where baseline PNGs live in every module. Committed to git
 * so reviewers see snapshot diffs in PRs.
 */
const val SnapshotDirectory: String = "src/test/snapshots"

@OptIn(ExperimentalRoborazziApi::class)
private val DefaultRoborazziOptions = RoborazziOptions(
    compareOptions = RoborazziOptions.CompareOptions(
        changeThreshold = SnapshotChangeThreshold,
    ),
)

/**
 * Captures the receiver composable into `[SnapshotDirectory]/[name].png` using
 * Tishina's project-wide tolerance.
 *
 * Usage:
 * ```
 * composeTestRule.onRoot().captureSnapshot("MeasureScreen_idle_light")
 * ```
 */
fun SemanticsNodeInteraction.captureSnapshot(name: String) {
    captureRoboImage(
        filePath = "$SnapshotDirectory/$name.png",
        roborazziOptions = DefaultRoborazziOptions,
    )
}
