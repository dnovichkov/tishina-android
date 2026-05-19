package ru.dmdp.tishina

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LocalizationTest {

    @Test
    @Config(qualifiers = "ru")
    fun `app_name resolves to Russian on the ru locale`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Тишина", context.getString(R.string.app_name))
        assertEquals("Тишина — измеритель шума", context.getString(R.string.app_full_name))
    }

    @Test
    @Config(qualifiers = "en")
    fun `app_name resolves to English fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Tisha", context.getString(R.string.app_name))
        assertEquals("Tisha — Sound Level Meter", context.getString(R.string.app_full_name))
    }
}
