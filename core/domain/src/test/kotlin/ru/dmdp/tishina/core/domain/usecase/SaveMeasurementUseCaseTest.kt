package ru.dmdp.tishina.core.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

@DisplayName("SaveMeasurementUseCase — input validation + repository proxy")
class SaveMeasurementUseCaseTest {

    private val repository = mockk<MeasurementRepository>(relaxed = true)
    private val useCase = SaveMeasurementUseCase(repository)

    private fun newMeasurement(
        title: String? = "Спальня",
        note: String? = "ok",
        samples: List<SoundSample> = listOf(SoundSample(40f, 0L), SoundSample(50f, 200L)),
    ) = NewMeasurement(
        createdAtEpochMs = 1_700_000_000_000L,
        durationMs = 30_000L,
        avgDb = 45f,
        minDb = 30f,
        maxDb = 60f,
        title = title,
        note = note,
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 44_100,
        samples = samples,
    )

    @Test
    fun `happy path returns Result success with the id from the repository`() = runTest {
        coEvery { repository.save(any()) } returns 42L

        val result = useCase(newMeasurement())

        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrNull())
        coVerify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `title at exactly MAX_TITLE_LENGTH characters is allowed`() = runTest {
        coEvery { repository.save(any()) } returns 1L
        val title = "a".repeat(NewMeasurement.MAX_TITLE_LENGTH)

        val result = useCase(newMeasurement(title = title))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `title one character over MAX_TITLE_LENGTH is rejected with IllegalArgumentException`() = runTest {
        val title = "a".repeat(NewMeasurement.MAX_TITLE_LENGTH + 1)

        val result = useCase(newMeasurement(title = title))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `note at exactly MAX_NOTE_LENGTH characters is allowed`() = runTest {
        coEvery { repository.save(any()) } returns 5L
        val note = "n".repeat(NewMeasurement.MAX_NOTE_LENGTH)

        val result = useCase(newMeasurement(note = note))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `note one character over MAX_NOTE_LENGTH is rejected`() = runTest {
        val note = "n".repeat(NewMeasurement.MAX_NOTE_LENGTH + 1)

        val result = useCase(newMeasurement(note = note))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `empty samples list is rejected with IllegalArgumentException`() = runTest {
        val result = useCase(newMeasurement(samples = emptyList()))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `null title is allowed`() = runTest {
        coEvery { repository.save(any()) } returns 1L

        val result = useCase(newMeasurement(title = null))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `null note is allowed`() = runTest {
        coEvery { repository.save(any()) } returns 1L

        val result = useCase(newMeasurement(note = null))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `samples with increasing timestamp are forwarded verbatim to the repository`() = runTest {
        val slot = slot<NewMeasurement>()
        coEvery { repository.save(capture(slot)) } returns 1L
        val samples = listOf(
            SoundSample(40f, 0L),
            SoundSample(50f, 200L),
            SoundSample(60f, 400L),
        )

        useCase(newMeasurement(samples = samples))

        assertEquals(samples, slot.captured.samples)
    }

    @Test
    fun `repository exception is wrapped into Result failure`() = runTest {
        val boom = IllegalStateException("disk full")
        coEvery { repository.save(any()) } throws boom

        val result = useCase(newMeasurement())

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun `NaN avg dB is rejected with IllegalArgumentException`() = runTest {
        val result = useCase(newMeasurement().copy(avgDb = Float.NaN))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `infinite min dB is rejected`() = runTest {
        val result = useCase(newMeasurement().copy(minDb = Float.NEGATIVE_INFINITY))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `infinite max dB is rejected`() = runTest {
        val result = useCase(newMeasurement().copy(maxDb = Float.POSITIVE_INFINITY))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `validation order title before note before samples short-circuits`() = runTest {
        // All three rules violated at once; the title rule fires first.
        val title = "a".repeat(NewMeasurement.MAX_TITLE_LENGTH + 1)
        val note = "n".repeat(NewMeasurement.MAX_NOTE_LENGTH + 1)

        val result = useCase(
            newMeasurement(title = title, note = note, samples = emptyList()),
        )

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message
        assertNull(message?.takeIf { !it.contains("title") })
        coVerify(exactly = 0) { repository.save(any()) }
    }
}
