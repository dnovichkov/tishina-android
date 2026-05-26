package ru.dmdp.tishina.core.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.ExportFilter
import ru.dmdp.tishina.core.domain.repository.MeasurementsExporter

/**
 * Contract for [ExportHistoryUseCase] — the thin orchestration layer that drives FR-20
 * CSV export from the ViewModel side.
 *
 * The use-case is intentionally minimal: it forwards [target] + [filter] to the
 * exporter and surfaces its `Result<Int>` (rows written) unchanged. Reasons for keeping
 * it as a use-case despite the apparent triviality:
 *
 *  - Hilt graph symmetry — ViewModels inject use-cases, not infrastructure abstractions
 *    (mirror of GetMeasurementsUseCase / DeleteMeasurementUseCase).
 *  - Future-proof — when v1.1 adds date-range / text-search filters (§ 32 of the spec)
 *    the logic lives here without churning the ViewModel API.
 *  - Test seam — pure-Kotlin tests can drive the use-case with a fake exporter, since
 *    [MeasurementsExporter] is a `:core:domain` interface that hides the Android `Uri`
 *    coupling behind a `targetUriString: String` opaque token.
 */
@DisplayName("ExportHistoryUseCase — orchestration for FR-20 CSV export")
class ExportHistoryUseCaseTest {

    /**
     * In-memory exporter that records the last call arguments. Allows verifying the
     * use-case forwards `target` and `filter` verbatim without inspecting any Android
     * dependency.
     */
    private class RecordingExporter(private val response: Result<Int> = Result.success(0)) : MeasurementsExporter {
        var lastTarget: String? = null
        var lastFilter: ExportFilter? = null
        var callCount: Int = 0

        override suspend fun export(targetUriString: String, filter: ExportFilter): Result<Int> {
            callCount++
            lastTarget = targetUriString
            lastFilter = filter
            return response
        }
    }

    @Test
    fun `invoke forwards target and filter ALL to the exporter and returns its result`() = runTest {
        val exporter = RecordingExporter(response = Result.success(3))
        val useCase = ExportHistoryUseCase(exporter)

        val result = useCase("content://docs/123", ExportFilter.All)

        assertEquals(1, exporter.callCount)
        assertEquals("content://docs/123", exporter.lastTarget)
        assertSame(ExportFilter.All, exporter.lastFilter)
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrNull())
    }

    @Test
    fun `invoke forwards filter ByIds with the exact id set`() = runTest {
        val exporter = RecordingExporter(response = Result.success(2))
        val useCase = ExportHistoryUseCase(exporter)

        val filter = ExportFilter.ByIds(setOf(7L, 11L))
        val result = useCase("content://anywhere", filter)

        assertEquals(filter, exporter.lastFilter)
        assertEquals(2, result.getOrNull())
    }

    @Test
    fun `empty result from exporter returns success with zero rows`() = runTest {
        val exporter = RecordingExporter(response = Result.success(0))
        val useCase = ExportHistoryUseCase(exporter)

        val result = useCase("content://target", ExportFilter.All)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull())
    }

    @Test
    fun `exporter failure propagates verbatim through the use-case`() = runTest {
        val failure = IllegalStateException("contentResolver returned null OutputStream")
        val exporter = RecordingExporter(response = Result.failure(failure))
        val useCase = ExportHistoryUseCase(exporter)

        val result = useCase("content://nope", ExportFilter.All)

        assertFalse(result.isSuccess)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun `ByIds with empty set still calls exporter - filtering is exporter's job`() = runTest {
        val exporter = RecordingExporter(response = Result.success(0))
        val useCase = ExportHistoryUseCase(exporter)

        useCase("content://x", ExportFilter.ByIds(emptySet()))

        assertEquals(1, exporter.callCount)
        // Use-case does NOT short-circuit on empty ids — the exporter decides whether to
        // emit a header-only file. Keeps the contract single-responsibility.
        assertEquals(ExportFilter.ByIds(emptySet()), exporter.lastFilter)
    }
}
