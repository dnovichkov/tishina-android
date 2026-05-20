package ru.dmdp.tishina.core.testing.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.model.MeasurementSummary
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.repository.MeasurementRepository

/**
 * In-memory [MeasurementRepository] for unit-testing ViewModels and use-cases
 * without an Android-side Room database.
 *
 * Backed by a single [MutableStateFlow]<Map<Long, MeasurementDetails>> — every
 * mutation is atomic and [observeSummaries] picks up the new state via [map],
 * which mirrors how the real implementation's Room-driven `Flow` reacts to
 * writes.
 *
 * The id sequence starts at 1 and increments monotonically; callers can read it
 * back from the returned `Long` of [save].
 *
 * Optional knobs:
 * - [saveError] — when set, the next [save] call throws this exception. Tests
 *   use it to drive the "save failed" branch of MeasureViewModel.
 *
 * Sparkline previews are computed naively from the input samples (≤ 20 evenly
 * spaced points). Tests that don't care about that wire pass empty samples in
 * via [seed] / [save].
 */
// `open` so tests can override individual operations to simulate Room IO failures
// without expanding the knob surface (e.g. `getByIdError`, `updateNoteError`, …).
open class FakeMeasurementRepository : MeasurementRepository {

    private val store = MutableStateFlow<Map<Long, MeasurementDetails>>(emptyMap())
    private var nextId: Long = 1L

    /** If non-null, the next [save] call throws this exception then resets. */
    @Volatile
    var saveError: Throwable? = null

    override fun observeSummaries(): Flow<List<MeasurementSummary>> = store.map { snapshot ->
        snapshot.values
            .map { it.summary }
            .sortedByDescending { it.createdAtEpochMs }
    }

    override suspend fun getById(id: Long): MeasurementDetails? = store.value[id]

    override suspend fun save(measurement: NewMeasurement): Long {
        saveError?.let { error ->
            saveError = null
            throw error
        }
        return insert(measurement)
    }

    override suspend fun delete(id: Long) {
        store.update { it - id }
    }

    override suspend fun updateNote(id: Long, note: String?) {
        store.update { snapshot ->
            val existing = snapshot[id] ?: return@update snapshot
            val updated = existing.copy(summary = existing.summary.copy(note = note))
            snapshot + (id to updated)
        }
    }

    /** Pre-populate the fake without going through suspend [save]. Returns ids in order. */
    fun seed(measurements: List<NewMeasurement>): List<Long> = measurements.map { insert(it) }

    /** Direct read of the current store size — convenient for assertions. */
    fun size(): Int = store.value.size

    private fun insert(measurement: NewMeasurement): Long {
        val id = nextId++
        val sparkline = buildSparkline(measurement)
        val summary = MeasurementSummary(
            id = id,
            createdAtEpochMs = measurement.createdAtEpochMs,
            durationMs = measurement.durationMs,
            avgDb = measurement.avgDb,
            minDb = measurement.minDb,
            maxDb = measurement.maxDb,
            title = measurement.title,
            note = measurement.note,
            sparklinePreview = sparkline,
        )
        val details = MeasurementDetails(
            summary = summary,
            samples = measurement.samples,
            weighting = measurement.weighting,
            timeWeighting = measurement.timeWeighting,
            calibrationOffsetDb = measurement.calibrationOffsetDb,
            sampleRateHz = measurement.sampleRateHz,
        )
        store.update { it + (id to details) }
        return id
    }

    private fun buildSparkline(measurement: NewMeasurement): List<Float> {
        val samples = measurement.samples
        if (samples.isEmpty()) return emptyList()
        if (samples.size <= SPARKLINE_POINTS) return samples.map { it.db }
        val step = samples.size.toDouble() / SPARKLINE_POINTS
        return (0 until SPARKLINE_POINTS).map { i ->
            samples[(i * step).toInt().coerceAtMost(samples.size - 1)].db
        }
    }

    private companion object {
        const val SPARKLINE_POINTS = 20
    }
}
