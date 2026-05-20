package ru.dmdp.tishina.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AppSettingsSnapshot — combined config + appearance")
class AppSettingsSnapshotTest {

    @Test
    fun `holds both measurement config and appearance settings`() {
        val config = MeasurementConfig(calibrationOffsetDb = 3.5f)
        val appearance = AppearanceSettings(themeMode = ThemeMode.Dark)

        val snapshot = AppSettingsSnapshot(config, appearance)

        assertEquals(config, snapshot.config)
        assertEquals(appearance, snapshot.appearance)
    }

    @Test
    fun `equality is value-based across both fields`() {
        val a = AppSettingsSnapshot(MeasurementConfig(), AppearanceSettings())
        val b = AppSettingsSnapshot(MeasurementConfig(), AppearanceSettings())
        assertEquals(a, b)
    }

    @Test
    fun `copy lets you override config independently`() {
        val original = AppSettingsSnapshot(MeasurementConfig(), AppearanceSettings())
        val updated = original.copy(config = MeasurementConfig(calibrationOffsetDb = 7f))

        assertEquals(7f, updated.config.calibrationOffsetDb)
        assertEquals(AppearanceSettings(), updated.appearance)
    }
}
