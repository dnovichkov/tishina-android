package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.model.ExportFilter
import ru.dmdp.tishina.core.domain.repository.MeasurementsExporter

/**
 * Orchestrates an FR-20 CSV export: receives a SAF URI string and a filter from the
 * ViewModel, forwards both to the [MeasurementsExporter]. The return value is the
 * exporter's `Result<Int>` (rows written or failure) — no transformation.
 *
 * The wrapper is kept despite the apparent triviality so:
 *  - Hilt-injected ViewModels depend on a domain use-case, mirroring the rest of the
 *    `:feature:history` graph (`GetMeasurementsUseCase`, `DeleteMeasurementUseCase`).
 *  - v1.1 enrichment (date-range filter, retention checks) lives here without churning
 *    the ViewModel API.
 */
class ExportHistoryUseCase(private val exporter: MeasurementsExporter) {

    suspend operator fun invoke(targetUriString: String, filter: ExportFilter): Result<Int> =
        exporter.export(targetUriString, filter)
}
