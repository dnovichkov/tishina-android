package ru.dmdp.tishina.core.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

@DisplayName("UpdateMeasurementNoteUseCase — note length validation + repository proxy")
class UpdateMeasurementNoteUseCaseTest {

    private val repository = mockk<MeasurementRepository>(relaxed = true)
    private val useCase = UpdateMeasurementNoteUseCase(repository)

    @Test
    fun `note within limit is forwarded as-is`() = runTest {
        val note = "n".repeat(NewMeasurement.MAX_NOTE_LENGTH)

        val result = useCase(7L, note)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.updateNote(7L, note) }
    }

    @Test
    fun `null note clears the field (no validation needed)`() = runTest {
        val result = useCase(7L, null)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.updateNote(7L, null) }
    }

    @Test
    fun `note one character over MAX_NOTE_LENGTH is rejected`() = runTest {
        val tooLong = "n".repeat(NewMeasurement.MAX_NOTE_LENGTH + 1)

        val result = useCase(7L, tooLong)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        coVerify(exactly = 0) { repository.updateNote(any(), any()) }
    }

    @Test
    fun `empty string is a valid note value`() = runTest {
        val result = useCase(7L, "")

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.updateNote(7L, "") }
    }

    @Test
    fun `repository exception is wrapped into Result failure`() = runTest {
        val boom = IllegalStateException("disk full")
        coEvery { repository.updateNote(7L, "ok") } throws boom

        val result = useCase(7L, "ok")

        assertTrue(result.isFailure)
        assertSame(boom, result.exceptionOrNull())
    }
}
