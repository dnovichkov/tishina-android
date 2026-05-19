package ru.dmdp.tishina.core.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.MeasurementSnapshot

@DisplayName("ResetMeasurementUseCase — returns an empty snapshot")
class ResetMeasurementUseCaseTest {

    @Test
    fun `produces the same canonical empty snapshot`() {
        val first = ResetMeasurementUseCase().invoke()
        val second = ResetMeasurementUseCase().invoke()
        assertEquals(MeasurementSnapshot.empty, first)
        assertEquals(first, second)
    }

    @Test
    fun `is stateless — multiple invocations on the same instance produce equal results`() {
        val useCase = ResetMeasurementUseCase()
        val a = useCase()
        val b = useCase()
        assertEquals(a, b)
        assertEquals(MeasurementSnapshot.empty, a)
    }
}
