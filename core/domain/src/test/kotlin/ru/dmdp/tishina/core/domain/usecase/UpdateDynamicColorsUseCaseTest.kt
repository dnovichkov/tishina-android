package ru.dmdp.tishina.core.domain.usecase

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

@DisplayName("UpdateDynamicColorsUseCase — Material You toggle")
class UpdateDynamicColorsUseCaseTest {

    private val repository = mockk<SettingsRepository>(relaxed = true)
    private val useCase = UpdateDynamicColorsUseCase(repository)

    @Test
    fun `invoke true proxies to repository`() = runTest {
        useCase(true)
        coVerify(exactly = 1) { repository.updateDynamicColors(true) }
    }

    @Test
    fun `invoke false proxies to repository`() = runTest {
        useCase(false)
        coVerify(exactly = 1) { repository.updateDynamicColors(false) }
    }
}
