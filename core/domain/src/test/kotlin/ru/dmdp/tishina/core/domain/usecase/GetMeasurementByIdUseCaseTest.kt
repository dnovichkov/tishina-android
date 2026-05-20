package ru.dmdp.tishina.core.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

@DisplayName("GetMeasurementByIdUseCase — repository lookup with null on miss")
class GetMeasurementByIdUseCaseTest {

    private val repository = mockk<MeasurementRepository>()
    private val useCase = GetMeasurementByIdUseCase(repository)

    private val details = MeasurementDetails(
        summary = MeasurementSummary(
            id = 5L,
            createdAtEpochMs = 100L,
            durationMs = 1_000L,
            avgDb = 50f,
            minDb = 40f,
            maxDb = 60f,
            title = "X",
            note = null,
            sparklinePreview = emptyList(),
        ),
        samples = emptyList(),
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 44_100,
    )

    @Test
    fun `returns MeasurementDetails when the repository has the id`() = runTest {
        coEvery { repository.getById(5L) } returns details

        val result = useCase(5L)

        assertEquals(details, result)
        coVerify(exactly = 1) { repository.getById(5L) }
    }

    @Test
    fun `returns null when the repository does not have the id`() = runTest {
        coEvery { repository.getById(99L) } returns null

        val result = useCase(99L)

        assertNull(result)
    }
}
