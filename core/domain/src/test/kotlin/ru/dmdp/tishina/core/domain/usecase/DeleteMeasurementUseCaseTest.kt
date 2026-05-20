package ru.dmdp.tishina.core.domain.usecase

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

@DisplayName("DeleteMeasurementUseCase — proxy to repository delete (idempotent)")
class DeleteMeasurementUseCaseTest {

    private val repository = mockk<MeasurementRepository>(relaxed = true)
    private val useCase = DeleteMeasurementUseCase(repository)

    @Test
    fun `calls repository delete with the supplied id`() = runTest {
        useCase(42L)

        coVerify(exactly = 1) { repository.delete(42L) }
    }

    @Test
    fun `unknown id forwards through unchanged (idempotent contract)`() = runTest {
        useCase(99_999L)

        coVerify(exactly = 1) { repository.delete(99_999L) }
    }

    @Test
    fun `multiple deletes are forwarded one-to-one`() = runTest {
        useCase(1L)
        useCase(2L)
        useCase(3L)

        coVerify(exactly = 1) { repository.delete(1L) }
        coVerify(exactly = 1) { repository.delete(2L) }
        coVerify(exactly = 1) { repository.delete(3L) }
    }
}
