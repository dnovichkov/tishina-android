package ru.dmdp.tishina.core.data.version

import android.content.pm.PackageInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.domain.model.AppVersion

/**
 * Contract for [AppVersionProviderImpl] — reads name/code through the real
 * [android.content.pm.PackageManager] on Robolectric so the wiring stays runtime-accurate.
 *
 * We mutate the seeded `PackageInfo.versionName` / `versionCode` *before* the read so the
 * assertion captures the actual SQL-of-PackageManager path, not a hardcoded BuildConfig
 * constant — Phase Release will rev versionName from `0.1.0-foundation`, and this test
 * keeps passing without modification because the provider doesn't bake the value in.
 *
 * The provider is `suspend` and hops onto an IO dispatcher; tests inject an
 * [UnconfinedTestDispatcher] so the IO hop is observable in the same `runTest` virtual
 * clock as the call site.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class AppVersionProviderImplTest {

    private lateinit var provider: AppVersionProviderImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        provider = AppVersionProviderImpl(context, UnconfinedTestDispatcher())
    }

    @Test
    fun `get returns AppVersion with PackageManager values`() = runTest {
        seedPackageInfo(versionName = "0.5.0-polish", versionCode = 42)

        val version = provider.get()

        assertEquals(AppVersion("0.5.0-polish", 42), version)
    }

    @Test
    fun `get coerces null versionName to empty string`() = runTest {
        seedPackageInfo(versionName = null, versionCode = 3)

        val version = provider.get()

        assertEquals("", version.versionName)
        assertEquals(3, version.versionCode)
    }

    @Test
    fun `displayName composes versionName and build number`() = runTest {
        seedPackageInfo(versionName = "1.0.0", versionCode = 7)

        val version = provider.get()

        assertEquals("1.0.0 (build 7)", version.displayName)
    }

    private fun seedPackageInfo(versionName: String?, versionCode: Int) {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val packageManager = context.packageManager
        val info: PackageInfo = packageManager.getPackageInfo(context.packageName, 0)
        info.versionName = versionName
        @Suppress("DEPRECATION")
        info.versionCode = versionCode
        shadowOf(packageManager).installPackage(info)
    }
}
