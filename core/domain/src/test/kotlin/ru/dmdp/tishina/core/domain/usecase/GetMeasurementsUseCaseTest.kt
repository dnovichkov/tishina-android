package ru.dmdp.tishina.core.domain.usecase

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

@DisplayName("GetMeasurementsUseCase — thin pass-through over repository flow")
class GetMeasurementsUseCaseTest {

    private val repository = mockk<MeasurementRepository>()
    private val useCase = GetMeasurementsUseCase(repository)

    private fun summary(id: Long, createdAt: Long) = MeasurementSummary(
        id = id,
        createdAtEpochMs = createdAt,
        durationMs = 30_000L,
        avgDb = 45f,
        minDb = 30f,
        maxDb = 60f,
        title = "M$id",
        note = null,
        sparklinePreview = emptyList(),
    )

    @Test
    fun `forwards the repository flow without modification`() = runTest {
        val data = listOf(summary(1L, 100L), summary(2L, 200L))
        every { repository.observeSummaries() } returns flowOf(data)

        useCase().test {
            assertEquals(data, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `empty list from repository is preserved`() = runTest {
        every { repository.observeSummaries() } returns flowOf(emptyList())

        useCase().test {
            assertEquals(emptyList<MeasurementSummary>(), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun `multiple emissions are passed through in order`() = runTest {
        val first = listOf(summary(1L, 100L))
        val second = listOf(summary(1L, 100L), summary(2L, 200L))
        every { repository.observeSummaries() } returns flowOf(first, second)

        useCase().test {
            assertEquals(first, awaitItem())
            assertEquals(second, awaitItem())
            awaitComplete()
        }
    }
}
