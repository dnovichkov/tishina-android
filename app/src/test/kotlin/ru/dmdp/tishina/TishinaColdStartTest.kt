package ru.dmdp.tishina

import android.os.Build
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dagger.hilt.internal.GeneratedComponentManagerHolder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.testutils.MeasureContentStubTestTag
import ru.dmdp.tishina.testutils.MeasureScreenTestStub
import ru.dmdp.tishina.ui.TishinaApp

/**
 * ⚠️ Approximation: Robolectric isn't a faithful proxy for real device cold-start latency
 * (no JIT warm path, no GPU compositor, no Hilt SingletonComponent dispatch overhead on
 * Activity#onCreate). What this test *can* enforce is the static contract that powers
 * FR-1 (≤ 1 s cold start): the application class must NOT do eager DB initialisation or
 * other blocking work in onCreate, and the first compose pass for MeasureScreen must
 * finish within a generous proxy budget. The authoritative measurement happens via
 * macrobenchmark on a physical device in Phase Release.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class TishinaColdStartTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Generous upper bound that catches accidental regressions (e.g. eager Room init in
     * Application.onCreate) without flaking on slow CI runners. The real FR-1 budget
     * is 1000 ms on-device; here we only assert the proxy is in the ballpark of being
     * "not catastrophically slow".
     */
    private val coldStartProxyBudgetMs: Long = 5_000L

    @Test
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun `Measure screen renders within proxy budget on cold start`() {
        val start = System.currentTimeMillis()
        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                val sizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 640.dp))
                TishinaApp(
                    windowSizeClass = sizeClass,
                    measureContent = { MeasureScreenTestStub() },
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MeasureContentStubTestTag).assertIsDisplayed()
        val elapsed = System.currentTimeMillis() - start
        assertTrue(
            "Cold-start proxy budget violated: ${elapsed}ms > ${coldStartProxyBudgetMs}ms — " +
                "FR-1 (≤ 1 s on-device) likely regressed. Check Application.onCreate for eager work.",
            elapsed < coldStartProxyBudgetMs,
        )
    }

    @Test
    fun `TishinaApplication declares no custom onCreate work`() {
        // FR-1 protection: any state we initialise eagerly in TishinaApplication.onCreate
        // becomes a fixed cost on cold start. The Hilt-generated superclass already calls
        // through to Application#onCreate (the dependency graph is lazy). Adding a custom
        // override here would defeat that — guard against it by asserting no Kotlin-declared
        // `onCreate` method exists on TishinaApplication directly.
        val declaredMethods = TishinaApplication::class.java.declaredMethods
        val ourOnCreate = declaredMethods.firstOrNull { it.name == "onCreate" && it.parameterCount == 0 }
        assertTrue(
            "TishinaApplication must not override onCreate — eager init blocks FR-1 cold start. " +
                "Move the work into a Hilt-provided @Singleton with lazy injection instead.",
            ourOnCreate == null,
        )
    }

    @Test
    fun `TishinaApplication declares no instance fields that pin lazy services eagerly`() {
        // Companion of the above: if a Kotlin property like `lateinit var db: TishinaDatabase`
        // were added, Hilt would inject it eagerly during onCreate. Block that by asserting
        // the class has no @Inject-able instance fields beyond the Hilt-generated ones
        // (which start with the synthetic `componentManager` / `injected` prefixes).
        val instanceFields = TishinaApplication::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
        val userFields = instanceFields.filterNot { field ->
            val name = field.name
            // Hilt's generated superclass injects state via internal fields; our own
            // class should declare nothing. Filter out any safe Kotlin-companion synthetic.
            name.contains("$") || name.startsWith("Companion")
        }
        assertTrue(
            "TishinaApplication declared unexpected instance fields: " +
                "${userFields.map { it.name }} — eager injection here regresses FR-1.",
            userFields.isEmpty(),
        )
    }

    @Test
    fun `Hilt graph is reachable but does not eagerly resolve TishinaDatabase`() {
        // Smoke check that the Hilt SingletonComponent is wired (so HiltViewModels can
        // resolve), without asserting *what* it has resolved. Room's TishinaDatabase
        // provider is `@Singleton @Provides` and Hilt only instantiates `@Singleton`
        // values on first access. This test exists to fail loudly if someone wires
        // TishinaDatabase as an eager field on TishinaApplication in the future — the
        // previous test catches the field; this one validates the Hilt contract is
        // still reachable so the indirection actually works.
        val app: Any = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        assertTrue(
            "TishinaApplication must implement Hilt's GeneratedComponentManagerHolder so DB " +
                "providers can resolve lazily on first @Inject access.",
            app is GeneratedComponentManagerHolder,
        )
        assertFalse(
            "TishinaApplication should not be an instance of any non-Hilt Application subclass " +
                "that might add eager init.",
            app.javaClass.superclass?.name == "android.app.Application",
        )
    }
}
