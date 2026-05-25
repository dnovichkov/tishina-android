package ru.dmdp.tishina.feature.about

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.AppVersion
import ru.dmdp.tishina.core.domain.model.OssLicense
import ru.dmdp.tishina.core.domain.repository.AppVersionProvider
import ru.dmdp.tishina.core.domain.repository.OssLicensesProvider
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule

/**
 * Contract for [AboutViewModel] — FR-21 AboutScreen state pipeline.
 *
 * Both providers are synchronous: `AppVersionProvider.get()` reads `PackageManager`
 * (cheap, in-process), `OssLicensesProvider.load()` reads a small asset (single
 * disk read at first call). The ViewModel still pushes them onto `viewModelScope`
 * so a slow provider can't block the main thread; tests use a `StandardTestDispatcher`
 * to control when the resolve actually fires.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("AboutViewModel — resolves version + OSS licenses for FR-21")
class AboutViewModelTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(StandardTestDispatcher())

    private val sampleVersion = AppVersion("0.5.0-polish", 42)
    private val sampleLicenses = listOf(
        OssLicense("Kotlin", "2.0.21", "Apache-2.0", "https://kotlinlang.org"),
        OssLicense("Room", "2.8.4", "Apache-2.0", "https://developer.android.com/training/data-storage/room"),
    )

    @Test
    fun `initial state shows loading with empty defaults`() = runTest {
        val versionProvider = mockk<AppVersionProvider> { every { get() } returns sampleVersion }
        val licensesProvider = mockk<OssLicensesProvider> { every { load() } returns sampleLicenses }

        val viewModel = AboutViewModel(versionProvider, licensesProvider)

        viewModel.state.test {
            val first = awaitItem()
            assertTrue(first.loading)
            assertEquals("", first.version.versionName)
            assertTrue(first.ossLicenses.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `state resolves to loaded snapshot with version and licenses`() = runTest {
        val versionProvider = mockk<AppVersionProvider> { every { get() } returns sampleVersion }
        val licensesProvider = mockk<OssLicensesProvider> { every { load() } returns sampleLicenses }

        val viewModel = AboutViewModel(versionProvider, licensesProvider)

        viewModel.state.test {
            awaitItem() // initial loading
            advanceUntilIdle()
            val loaded = awaitItem()
            assertFalse(loaded.loading)
            assertEquals(sampleVersion, loaded.version)
            assertEquals(sampleLicenses, loaded.ossLicenses)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `empty license list still settles loading to false`() = runTest {
        val versionProvider = mockk<AppVersionProvider> { every { get() } returns sampleVersion }
        val licensesProvider = mockk<OssLicensesProvider> { every { load() } returns emptyList() }

        val viewModel = AboutViewModel(versionProvider, licensesProvider)

        viewModel.state.test {
            awaitItem()
            advanceUntilIdle()
            val loaded = awaitItem()
            assertFalse(loaded.loading)
            assertTrue(loaded.ossLicenses.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }
}
