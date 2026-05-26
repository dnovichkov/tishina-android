package ru.dmdp.tishina.core.testing.fakes

import ru.dmdp.tishina.core.domain.model.ExportFilter
import ru.dmdp.tishina.core.domain.repository.MeasurementsExporter
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory [MeasurementsExporter] for tests that don't exercise the real CSV pipeline.
 *
 * `FakeMeasurementsExporter()` returns `Result.success(0)` for every call — adequate as a
 * neutral stand-in when a ViewModel test pulls in `HistoryViewModel` (which now declares an
 * [ExportHistoryUseCase] dependency) but the test itself does not assert on the export path.
 *
 * Tests that *do* care about the export should use the inline `RecordingExporter` patterns in
 * [ru.dmdp.tishina.feature.history.HistoryViewModelExportTest] which capture the last
 * `target` + `filter` and let the test programme the response.
 */
class FakeMeasurementsExporter(private val response: Result<Int> = Result.success(0)) : MeasurementsExporter {

    val callCount: AtomicInteger = AtomicInteger(0)
    var lastTarget: String? = null
        private set
    var lastFilter: ExportFilter? = null
        private set

    override suspend fun export(targetUriString: String, filter: ExportFilter): Result<Int> {
        callCount.incrementAndGet()
        lastTarget = targetUriString
        lastFilter = filter
        return response
    }
}
