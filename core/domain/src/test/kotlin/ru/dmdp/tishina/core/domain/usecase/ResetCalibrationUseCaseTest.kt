package ru.dmdp.tishina.core.domain.usecase

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

@DisplayName("ResetCalibrationUseCase — FR-19 reset button delegate")
class ResetCalibrationUseCaseTest {

    private val repository = mockk<SettingsRepository>(relaxed = true)
    private val useCase = ResetCalibrationUseCase(repository)

    @Test
    fun `invoke delegates to repository_resetCalibration`() = runTest {
        useCase()

        coVerify(exactly = 1) { repository.resetCalibration() }
    }

    @Test
    fun `repeated invocations are idempotent and pass through every time`() = runTest {
        useCase()
        useCase()
        useCase()

        coVerify(exactly = 3) { repository.resetCalibration() }
    }
}
