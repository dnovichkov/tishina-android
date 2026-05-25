package ru.dmdp.tishina.core.data.licenses

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end contract for [OssLicensesProviderImpl] — uses Robolectric's AssetManager
 * to verify the missing-asset path. Phase 5 asset itself ships in `:app/src/main/assets/`
 * which is unavailable to a `:core:data` unit test, so the assertion here is the
 * graceful-degradation case (empty list, no crash) — the happy path is covered by
 * [OssLicensesParserTest] on pure JVM.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class OssLicensesProviderImplTest {

    @Test
    fun `load returns empty list when asset is absent`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val provider = OssLicensesProviderImpl(context)

        val licenses = provider.load()

        assertEquals(emptyList<Any>(), licenses)
        assertTrue(licenses.isEmpty())
    }
}
