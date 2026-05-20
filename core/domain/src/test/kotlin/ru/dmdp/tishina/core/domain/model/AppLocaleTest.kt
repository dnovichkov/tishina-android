package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AppLocale enum — System / Russian / English with BCP-47 tags (FR-18)")
class AppLocaleTest {

    @Test
    fun `enum exposes exactly three values`() {
        val values = AppLocale.entries
        assertEquals(3, values.size)
    }

    @Test
    fun `System locale carries empty tag (follows OS)`() {
        assertEquals("", AppLocale.System.tag)
    }

    @Test
    fun `Russian locale tag is 'ru'`() {
        assertEquals("ru", AppLocale.Russian.tag)
    }

    @Test
    fun `English locale tag is 'en'`() {
        assertEquals("en", AppLocale.English.tag)
    }

    @Test
    fun `enum lookup by name works`() {
        assertEquals(AppLocale.System, AppLocale.valueOf("System"))
        assertEquals(AppLocale.Russian, AppLocale.valueOf("Russian"))
        assertEquals(AppLocale.English, AppLocale.valueOf("English"))
    }
}
