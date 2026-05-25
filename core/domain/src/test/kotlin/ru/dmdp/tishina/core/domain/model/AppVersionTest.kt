package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AppVersion — FR-21 BuildConfig-derived version model")
class AppVersionTest {

    @Test
    fun `constructor stores versionName and versionCode`() {
        val version = AppVersion(versionName = "0.5.0-polish", versionCode = 42)

        assertEquals("0.5.0-polish", version.versionName)
        assertEquals(42, version.versionCode)
    }

    @Test
    @DisplayName("blank versionName is preserved verbatim (fallback path)")
    fun `blank versionName is preserved verbatim for UI to branch on`() {
        // The AppVersionProviderImpl FALLBACK on NameNotFoundException / system_server pressure
        // surfaces as AppVersion("", 0). The UI layer (AboutScreen) reads `versionName.isBlank()`
        // to choose between the "build-only" and the "name + build" string resource.
        val fallback = AppVersion(versionName = "", versionCode = 0)

        assertEquals("", fallback.versionName)
        assertEquals(0, fallback.versionCode)
    }

    @Test
    fun `data class equality compares both fields`() {
        val a = AppVersion("1.0.0", 1)
        val b = AppVersion("1.0.0", 1)
        val c = AppVersion("1.0.0", 2)

        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `copy preserves untouched fields`() {
        val original = AppVersion("0.1.0-foundation", 1)
        val copy = original.copy(versionCode = 5)

        assertEquals("0.1.0-foundation", copy.versionName)
        assertEquals(5, copy.versionCode)
    }
}
