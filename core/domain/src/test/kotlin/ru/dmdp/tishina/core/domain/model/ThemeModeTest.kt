package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ThemeMode enum — covers System / Light / Dark (FR-17)")
class ThemeModeTest {

    @Test
    fun `enum exposes exactly three values`() {
        val values = ThemeMode.entries
        assertEquals(3, values.size)
    }

    @Test
    fun `enum has System Light Dark values`() {
        assertEquals(ThemeMode.System, ThemeMode.valueOf("System"))
        assertEquals(ThemeMode.Light, ThemeMode.valueOf("Light"))
        assertEquals(ThemeMode.Dark, ThemeMode.valueOf("Dark"))
    }
}
