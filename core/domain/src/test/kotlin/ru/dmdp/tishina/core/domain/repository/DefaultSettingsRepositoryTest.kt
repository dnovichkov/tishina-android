package ru.dmdp.tishina.core.domain.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting

@DisplayName("DefaultSettingsRepository — phase-2 stub")
class DefaultSettingsRepositoryTest {

    private val repository = DefaultSettingsRepository()

    @Test
    fun `emits a single default config`() = runTest {
        repository.config.test {
            val emitted = awaitItem()
            assertEquals(MeasurementConfig(), emitted)
            awaitComplete()
        }
    }

    @Test
    fun `emits a single default appearance`() = runTest {
        repository.appearance.test {
            val emitted = awaitItem()
            assertEquals(AppearanceSettings(), emitted)
            awaitComplete()
        }
    }

    @Test
    fun `setters are no-ops and do not mutate observable config`() = runTest {
        repository.updateCalibrationOffset(5f)
        repository.updateFrequencyWeighting(FrequencyWeighting.Z)
        repository.updateTimeWeighting(TimeWeighting.SLOW)
        repository.resetCalibration()

        repository.config.test {
            assertEquals(MeasurementConfig(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `appearance setters are no-ops and do not mutate observable appearance`() = runTest {
        repository.updateThemeMode(ThemeMode.Dark)
        repository.updateDynamicColors(false)
        repository.updateAppLocale(AppLocale.Russian)

        repository.appearance.test {
            assertEquals(AppearanceSettings(), awaitItem())
            awaitComplete()
        }
    }
}
