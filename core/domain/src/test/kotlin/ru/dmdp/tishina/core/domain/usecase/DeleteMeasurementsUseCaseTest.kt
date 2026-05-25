package ru.dmdp.tishina.core.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

@DisplayName("DeleteMeasurementsUseCase — bulk delete with empty-set validation (FR-12)")
class DeleteMeasurementsUseCaseTest {

    private val repository = mockk<MeasurementRepository>(relaxed = true)
    private val useCase = DeleteMeasurementsUseCase(repository)

    @Test
    fun `non-empty set delegates to repository deleteAll and returns success`() = runTest {
        val ids = setOf(1L, 2L, 3L)

        val result = useCase(ids)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.deleteAll(setOf(1L, 2L, 3L)) }
    }

    @Test
    fun `empty set returns failure with IllegalArgumentException and does not touch repository`() = runTest {
        val result = useCase(emptySet())

        assertTrue(result.isFailure)
        val cause = result.exceptionOrNull()
        assertNotNull(cause)
        assertTrue(cause is IllegalArgumentException)
        coVerify(exactly = 0) { repository.deleteAll(any()) }
    }

    @Test
    fun `single-id set is allowed (not treated as empty)`() = runTest {
        val ids = setOf(42L)

        val result = useCase(ids)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.deleteAll(setOf(42L)) }
    }

    @Test
    fun `repeated invocations forward each call to repository (stateless use-case)`() = runTest {
        useCase(setOf(1L))
        useCase(setOf(1L))

        coVerify(exactly = 2) { repository.deleteAll(setOf(1L)) }
    }

    @Test
    fun `repository failure is wrapped as Result failure preserving the exception`() = runTest {
        val boom = IllegalStateException("disk corrupt")
        coEvery { repository.deleteAll(any()) } throws boom

        val result = useCase(setOf(1L, 2L))

        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }

    @Test
    fun `large batch (1000 ids) is forwarded unchanged — chunking is the data layer's concern`() = runTest {
        val ids = (1L..1000L).toSet()

        val result = useCase(ids)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.deleteAll(ids) }
    }

    @Test
    fun `unknown-id-only set is forwarded as-is (idempotent contract — Data layer no-ops)`() = runTest {
        val phantomIds = setOf(99_999L, 88_888L)

        val result = useCase(phantomIds)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.deleteAll(phantomIds) }
    }
}
