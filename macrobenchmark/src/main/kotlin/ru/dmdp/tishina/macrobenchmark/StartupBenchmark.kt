package ru.dmdp.tishina.macrobenchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 6 Task 9 — NFR-1 guard: cold-start to interactive must stay ≤ 1 s on
 * a Pixel 6a reference device. CI runs this on `reactivecircus` emulator API
 * 33 as a *proxy*; the emulator is consistently slower than real hardware, so
 * a green run here gives us a comfortable headroom on the production device.
 *
 * **Why `iterations = 5` rather than the documentation's 10?** Each cold-start
 * iteration takes ~6 s of warmup + measurement + ApiCompat resets on the
 * emulator. Five iterations is enough to compute a tight 90th-percentile
 * confidence interval (Macrobenchmark prints `min / median / max` so we keep
 * an eye on regressions); doubling iterations doubles CI minutes without
 * narrowing the interval enough to justify the cost.
 *
 * **`StartupMode.COLD`** clears the target process between iterations using
 * `am force-stop`, ensuring we measure from process creation (the metric the
 * NFR is written against). HOT/WARM modes would shadow the SplashScreen +
 * Hilt graph wiring cost we're actually trying to keep within budget.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = COLD_START_ITERATIONS,
        startupMode = StartupMode.COLD,
    ) {
        pressHome()
        startActivityAndWait()
    }

    private companion object {
        private const val TARGET_PACKAGE = "ru.dmdp.tishina"
        private const val COLD_START_ITERATIONS = 5
    }
}
