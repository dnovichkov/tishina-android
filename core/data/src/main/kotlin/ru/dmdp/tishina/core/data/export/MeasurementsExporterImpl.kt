package ru.dmdp.tishina.core.data.export

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.dmdp.tishina.core.data.db.dao.MeasurementDao
import ru.dmdp.tishina.core.data.di.IoDispatcher
import ru.dmdp.tishina.core.domain.model.ExportFilter
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.repository.MeasurementsExporter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SAF-backed [MeasurementsExporter]:
 *  1. Resolves [targetUriString] back to an `android.net.Uri`.
 *  2. Reads the first emission of [MeasurementDao.observeSummaries] — a snapshot of
 *     the current history without subscribing for further updates.
 *  3. Applies the [ExportFilter] in memory (so the DAO query stays simple and
 *     unchanged from the History list path — single source of truth for ordering).
 *  4. Streams everything through [CsvSerializer] into the `ContentResolver`-provided
 *     `OutputStream` (UTF-8, BOM, CRLF — see serializer kdoc).
 *
 * Failure modes captured as `Result.failure`:
 *  - `ContentResolver.openOutputStream(uri)` returns `null` (system picker handed
 *    us a URI the resolver can't open — rare but observed on emulators).
 *  - Any `IOException` from open or write.
 *
 * `CancellationException` is rethrown so structured-cancellation keeps working —
 * cancelling the calling coroutine should not be misreported as an export failure.
 */
@Singleton
class MeasurementsExporterImpl @Inject constructor(
    private val dao: MeasurementDao,
    private val contentResolver: ContentResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MeasurementsExporter {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun export(targetUriString: String, filter: ExportFilter): Result<Int> =
        withContext(ioDispatcher) {
            try {
                val uri = Uri.parse(targetUriString)
                val rows = loadRows(filter)
                val stream = contentResolver.openOutputStream(uri)
                    ?: return@withContext Result.failure(
                        IllegalStateException("ContentResolver.openOutputStream returned null for $uri"),
                    )
                stream.use { out ->
                    OutputStreamWriter(out, StandardCharsets.UTF_8).use { writer ->
                        CsvSerializer().serialize(rows, writer)
                    }
                }
                Result.success(rows.size)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                Result.failure(failure)
            }
        }

    /**
     * Snapshot the current history then filter in memory. We deliberately bypass the
     * full [MeasurementRepositoryImpl] reactive chain (which fans out sparkline-preview
     * queries per row) because exports do not need the sparkline column — the CSV
     * format only carries the scalar summary columns, so the extra IO would be waste.
     */
    private suspend fun loadRows(filter: ExportFilter): List<MeasurementSummary> {
        val rows = dao.observeSummaries().first().map { row ->
            MeasurementSummary(
                id = row.id,
                createdAtEpochMs = row.createdAtEpochMs,
                durationMs = row.durationMs,
                avgDb = row.avgDb,
                minDb = row.minDb,
                maxDb = row.maxDb,
                title = row.title,
                note = row.note,
                sparklinePreview = emptyList(),
            )
        }
        return when (filter) {
            ExportFilter.All -> rows
            is ExportFilter.ByIds -> rows.filter { it.id in filter.ids }
        }
    }
}
