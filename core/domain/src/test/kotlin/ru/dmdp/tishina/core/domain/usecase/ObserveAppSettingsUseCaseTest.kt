package ru.dmdp.tishina.core.domain.usecase

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.AppSettingsSnapshot
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

@DisplayName("ObserveAppSettingsUseCase — combines config + appearance")
class ObserveAppSettingsUseCaseTest {

    @Test
    fun `combines current config and appearance into a single snapshot`() = runTest {
        val configFlow = MutableStateFlow(MeasurementConfig())
        val appearanceFlow = MutableStateFlow(AppearanceSettings())
        val repository = mockk<SettingsRepository>().apply {
            every { config } returns configFlow
            every { appearance } returns appearanceFlow
        }
        val useCase = ObserveAppSettingsUseCase(repository)

        useCase().test {
            val snapshot = awaitItem()
            assertEquals(MeasurementConfig(), snapshot.config)
            assertEquals(AppearanceSettings(), snapshot.appearance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-emits when config flow changes`() = runTest {
        val configFlow = MutableStateFlow(MeasurementConfig())
        val appearanceFlow = MutableStateFlow(AppearanceSettings())
        val repository = mockk<SettingsRepository>().apply {
            every { config } returns configFlow
            every { appearance } returns appearanceFlow
        }
        val useCase = ObserveAppSettingsUseCase(repository)

        useCase().test {
            assertEquals(AppSettingsSnapshot(MeasurementConfig(), AppearanceSettings()), awaitItem())
            configFlow.value = MeasurementConfig(calibrationOffsetDb = 5f, timeWeighting = TimeWeighting.SLOW)
            val next = awaitItem()
            assertEquals(5f, next.config.calibrationOffsetDb)
            assertEquals(TimeWeighting.SLOW, next.config.timeWeighting)
            assertEquals(AppearanceSettings(), next.appearance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-emits when appearance flow changes`() = runTest {
        val configFlow = MutableStateFlow(MeasurementConfig())
        val appearanceFlow = MutableStateFlow(AppearanceSettings())
        val repository = mockk<SettingsRepository>().apply {
            every { config } returns configFlow
            every { appearance } returns appearanceFlow
        }
        val useCase = ObserveAppSettingsUseCase(repository)

        useCase().test {
            assertEquals(AppSettingsSnapshot(MeasurementConfig(), AppearanceSettings()), awaitItem())
            appearanceFlow.value = AppearanceSettings(
                themeMode = ThemeMode.Dark,
                dynamicColors = false,
                locale = AppLocale.English,
            )
            val next = awaitItem()
            assertEquals(MeasurementConfig(), next.config)
            assertEquals(ThemeMode.Dark, next.appearance.themeMode)
            assertEquals(false, next.appearance.dynamicColors)
            assertEquals(AppLocale.English, next.appearance.locale)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
