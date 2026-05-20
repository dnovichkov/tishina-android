package ru.dmdp.tishina.core.domain.usecase

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

@DisplayName("UpdateTimeWeightingUseCase — FR-16 Fast/Slow toggle")
class UpdateTimeWeightingUseCaseTest {

    private val repository = mockk<SettingsRepository>(relaxed = true)
    private val useCase = UpdateTimeWeightingUseCase(repository)

    @Test
    fun `invoke FAST proxies to repository`() = runTest {
        useCase(TimeWeighting.FAST)

        coVerify(exactly = 1) { repository.updateTimeWeighting(TimeWeighting.FAST) }
    }

    @Test
    fun `invoke SLOW proxies to repository`() = runTest {
        useCase(TimeWeighting.SLOW)

        coVerify(exactly = 1) { repository.updateTimeWeighting(TimeWeighting.SLOW) }
    }
}
