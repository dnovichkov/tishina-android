package ru.dmdp.tishina.core.testing

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Touches every `api`-exposed library so removing one from
 * `core/testing/build.gradle.kts` triggers an immediate compile error here
 * rather than a baffling failure later in a feature module.
 *
 * Symbols are referenced (not exercised under a runtime) to keep the smoke
 * test runner-agnostic — pulling in `@RunWith(RobolectricTestRunner)` would
 * defeat the goal of a fast JVM smoke that any consumer can run.
 */
class InfrastructureSmokeTest {

    interface Greeter {
        fun greet(): String
    }

    @Test
    fun mockkAndCoroutinesTestResolve() = runTest {
        val greeter = mockk<Greeter>()
        every { greeter.greet() } returns "hi"
        assertEquals("hi", greeter.greet())
    }

    @Test
    fun turbineResolves() = runTest {
        flowOf(1, 2, 3).test {
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())
            assertEquals(3, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun androidAndScreenshotToolchainAreOnClasspath() {
        assertNotNull(Class.forName("org.robolectric.Robolectric"))
        assertNotNull(Class.forName("com.github.takahirom.roborazzi.RoborazziOptions"))
        assertNotNull(Class.forName("androidx.compose.ui.test.junit4.ComposeContentTestRule"))
        assertNotNull(Class.forName("junit.framework.TestCase"))
    }
}
