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
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.repository.SettingsRepository

@DisplayName("UpdateCalibrationUseCase — FR-14 validation + rounding")
class UpdateCalibrationUseCaseTest {

    private val repository = mockk<SettingsRepository>(relaxed = true)
    private val useCase = UpdateCalibrationUseCase(repository)

    @Test
    fun `happy path forwards rounded value to the repository`() = runTest {
        val result = useCase(3.5f)

        assertTrue(result.isSuccess)
        assertEquals(3.5f, result.getOrNull())
        coVerify(exactly = 1) { repository.updateCalibrationOffset(3.5f) }
    }

    @Test
    fun `value at lower boundary -20_0 is accepted`() = runTest {
        val result = useCase(AppearanceSettings.CALIBRATION_MIN_DB)

        assertTrue(result.isSuccess)
        assertEquals(-20.0f, result.getOrNull())
        coVerify { repository.updateCalibrationOffset(-20.0f) }
    }

    @Test
    fun `value at upper boundary 20_0 is accepted`() = runTest {
        val result = useCase(AppearanceSettings.CALIBRATION_MAX_DB)

        assertTrue(result.isSuccess)
        assertEquals(20.0f, result.getOrNull())
        coVerify { repository.updateCalibrationOffset(20.0f) }
    }

    @Test
    fun `zero is accepted`() = runTest {
        val result = useCase(0f)

        assertTrue(result.isSuccess)
        assertEquals(0f, result.getOrNull())
        coVerify { repository.updateCalibrationOffset(0f) }
    }

    @Test
    fun `value below -20_0 is rejected with IllegalArgumentException`() = runTest {
        val result = useCase(-20.1f)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.updateCalibrationOffset(any()) }
    }

    @Test
    fun `value above 20_0 is rejected with IllegalArgumentException`() = runTest {
        val result = useCase(20.1f)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.updateCalibrationOffset(any()) }
    }

    @Test
    fun `value with sub-step precision is rounded to nearest 0_1 step`() = runTest {
        val result = useCase(3.55f)

        assertTrue(result.isSuccess)
        // 3.55 → 3.5 (half-down) или 3.6 (half-up). Float не представляет 3.6 точно,
        // поэтому сверяемся с допуском половины шага.
        val rounded = result.getOrNull()
        assertNotNull(rounded)
        val nonNull = rounded!!
        val nearLow = kotlin.math.abs(nonNull - 3.5f) < AppearanceSettings.CALIBRATION_STEP_DB / 2f
        val nearHigh = kotlin.math.abs(nonNull - 3.6f) < AppearanceSettings.CALIBRATION_STEP_DB / 2f
        assertTrue(nearLow || nearHigh, "Expected ~3.5 or ~3.6, was $nonNull")
        coVerify { repository.updateCalibrationOffset(nonNull) }
    }

    @Test
    fun `NaN is rejected with IllegalArgumentException`() = runTest {
        val result = useCase(Float.NaN)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.updateCalibrationOffset(any()) }
    }

    @Test
    fun `positive infinity is rejected`() = runTest {
        val result = useCase(Float.POSITIVE_INFINITY)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.updateCalibrationOffset(any()) }
    }

    @Test
    fun `repository exception is propagated as Result_failure`() = runTest {
        val boom = IllegalStateException("disk full")
        coEvery { repository.updateCalibrationOffset(any()) } throws boom

        val result = useCase(2.0f)

        assertTrue(result.isFailure)
        assertEquals(boom, result.exceptionOrNull())
    }
}
