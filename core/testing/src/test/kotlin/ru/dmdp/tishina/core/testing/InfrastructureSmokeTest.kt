package ru.dmdp.tishina.core.testing

import org.junit.jupiter.api.Test

/**
 * Trivial JUnit 5 test that proves the `:core:testing` module — and the
 * transitively exposed test toolchain (Robolectric, Roborazzi, Compose UI test,
 * MockK, Turbine, coroutines-test) — compiles and is wired into the Gradle
 * `testDebugUnitTest` task.
 *
 * Run by CI on every PR so a broken dependency in this central module is
 * caught here, not later as a baffling compile error in a feature module.
 */
class InfrastructureSmokeTest {

    @Test
    fun moduleCompiles() = Unit
}
