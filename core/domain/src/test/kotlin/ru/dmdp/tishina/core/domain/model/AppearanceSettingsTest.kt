package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AppearanceSettings — default values and copy semantics")
class AppearanceSettingsTest {

    @Test
    fun `default appearance matches FR-17 (System theme) and FR-18 (System locale)`() {
        val appearance = AppearanceSettings()
        assertEquals(ThemeMode.System, appearance.themeMode)
        assertTrue(appearance.dynamicColors, "Dynamic colors are enabled by default on supported devices")
        assertEquals(AppLocale.System, appearance.locale)
    }

    @Test
    fun `copy lets you override theme mode only`() {
        val updated = AppearanceSettings().copy(themeMode = ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, updated.themeMode)
        assertTrue(updated.dynamicColors)
        assertEquals(AppLocale.System, updated.locale)
    }

    @Test
    fun `copy lets you override dynamic colors only`() {
        val updated = AppearanceSettings().copy(dynamicColors = false)
        assertEquals(ThemeMode.System, updated.themeMode)
        assertEquals(false, updated.dynamicColors)
        assertEquals(AppLocale.System, updated.locale)
    }

    @Test
    fun `copy lets you override locale only`() {
        val updated = AppearanceSettings().copy(locale = AppLocale.Russian)
        assertEquals(ThemeMode.System, updated.themeMode)
        assertTrue(updated.dynamicColors)
        assertEquals(AppLocale.Russian, updated.locale)
    }

    @Test
    fun `equality is value-based`() {
        val a = AppearanceSettings(themeMode = ThemeMode.Dark)
        val b = AppearanceSettings(themeMode = ThemeMode.Dark)
        assertEquals(a, b)
    }

    @Test
    fun `differing appearance instances are not equal`() {
        val a = AppearanceSettings()
        val b = AppearanceSettings(themeMode = ThemeMode.Dark)
        assertNotEquals(a, b)
    }

    @Test
    fun `calibration constants from companion match FR-14 range with 0_1 step`() {
        assertEquals(-20.0f, AppearanceSettings.CALIBRATION_MIN_DB)
        assertEquals(20.0f, AppearanceSettings.CALIBRATION_MAX_DB)
        assertEquals(0.1f, AppearanceSettings.CALIBRATION_STEP_DB)
    }
}
