package ru.dmdp.tishina.core.testing.rules

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension that swaps `Dispatchers.Main` with a [TestDispatcher] for
 * the duration of a single test. Register with `@JvmField @RegisterExtension`:
 *
 * ```
 * @JvmField @RegisterExtension
 * val mainDispatcher = MainDispatcherRule()
 * ```
 *
 * Defaults to [StandardTestDispatcher] (manual virtual-time control). Pass an
 * `UnconfinedTestDispatcher` when a test needs eager coroutine execution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val testDispatcher: TestDispatcher = StandardTestDispatcher()) :
    BeforeEachCallback,
    AfterEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun afterEach(context: ExtensionContext) {
        Dispatchers.resetMain()
    }
}
