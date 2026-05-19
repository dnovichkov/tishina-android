package ru.dmdp.tishina.core.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.MeasurementSnapshot
import ru.dmdp.tishina.core.domain.model.SoundSample

@DisplayName("StopMeasurementUseCase — freezes the last snapshot")
class StopMeasurementUseCaseTest {

    @Test
    fun `returns the snapshot unchanged when the session has data`() {
        val snapshot = MeasurementSnapshot(
            currentDb = 65f,
            minDb = 30f,
            maxDb = 90f,
            avgDb = 60f,
            durationMs = 5_000L,
            recent = listOf(SoundSample(65f, 5_000L)),
        )

        val frozen = StopMeasurementUseCase().invoke(snapshot)

        assertEquals(snapshot, frozen)
    }

    @Test
    fun `returns the empty snapshot unchanged when nothing was captured`() {
        val frozen = StopMeasurementUseCase().invoke(MeasurementSnapshot.empty)
        assertSame(MeasurementSnapshot.empty, frozen)
    }
}
