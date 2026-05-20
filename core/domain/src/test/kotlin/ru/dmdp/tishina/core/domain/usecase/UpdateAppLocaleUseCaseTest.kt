package ru.dmdp.tishina.core.domain.usecase

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

@DisplayName("UpdateAppLocaleUseCase — FR-18 language switching")
class UpdateAppLocaleUseCaseTest {

    private val repository = mockk<SettingsRepository>(relaxed = true)
    private val useCase = UpdateAppLocaleUseCase(repository)

    @Test
    fun `invoke System proxies to repository`() = runTest {
        useCase(AppLocale.System)
        coVerify(exactly = 1) { repository.updateAppLocale(AppLocale.System) }
    }

    @Test
    fun `invoke Russian proxies to repository`() = runTest {
        useCase(AppLocale.Russian)
        coVerify(exactly = 1) { repository.updateAppLocale(AppLocale.Russian) }
    }

    @Test
    fun `invoke English proxies to repository`() = runTest {
        useCase(AppLocale.English)
        coVerify(exactly = 1) { repository.updateAppLocale(AppLocale.English) }
    }
}
