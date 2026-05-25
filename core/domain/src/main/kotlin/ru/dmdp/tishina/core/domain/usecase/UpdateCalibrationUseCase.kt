package ru.dmdp.tishina.core.domain.usecase

import kotlinx.coroutines.CancellationException
import ru.dmdp.tishina.core.domain.model.AppearanceSettings.Companion.CALIBRATION_MAX_DB
import ru.dmdp.tishina.core.domain.model.AppearanceSettings.Companion.CALIBRATION_MIN_DB
import ru.dmdp.tishina.core.domain.model.AppearanceSettings.Companion.CALIBRATION_STEP_DB
import ru.dmdp.tishina.core.domain.repository.SettingsRepository
import kotlin.math.roundToInt

/**
 * FR-14 — validates the calibration offset proposed by the UI (range, NaN,
 * infinity), snaps it to the [CALIBRATION_STEP_DB] grid and persists it through
 * [SettingsRepository]. Returns the rounded value on success or wraps the
 * failure in [Result.failure].
 *
 * Validation failures use [IllegalArgumentException] so the call site can
 * distinguish "user picked an out-of-range value" (UI message: out of range)
 * from a persistence failure thrown by [SettingsRepository] (UI message:
 * generic save error). The previous shape ran validation and persistence
 * inside the same `runCatching` block, which made disk I/O errors masquerade
 * as range-violation snackbars.
 */
class UpdateCalibrationUseCase(private val repository: SettingsRepository) {
    // Suppress TooGenericExceptionCaught: DataStore can throw a range of failure types
    // (IOException for disk failures, CorruptionException for serializer faults, plus any
    // implementation-specific Throwable from a custom SettingsRepository). The catch-all is
    // intentional — callers receive a generic Result.failure and surface a generic save-error
    // snackbar. CancellationException is rethrown above the generic catch to preserve
    // structured cancellation.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(db: Float): Result<Float> {
        if (!db.isFinite()) {
            return Result.failure(
                IllegalArgumentException("Calibration offset must be a finite number, was $db"),
            )
        }
        if (db !in CALIBRATION_MIN_DB..CALIBRATION_MAX_DB) {
            return Result.failure(
                IllegalArgumentException(
                    "Calibration offset $db out of range [$CALIBRATION_MIN_DB, $CALIBRATION_MAX_DB]",
                ),
            )
        }
        // Re-clamp after rounding: `0.1f` is not exactly representable, so multiplying back
        // can produce a value fractionally outside [-20, +20] for inputs sitting exactly on
        // the boundary (e.g. 20.0f → 20.000002f). Without this clamp the persisted value
        // would drift outside the advertised range, and any downstream validator that re-reads
        // the stored value would incorrectly reject it.
        val rounded = ((db / CALIBRATION_STEP_DB).roundToInt() * CALIBRATION_STEP_DB)
            .coerceIn(CALIBRATION_MIN_DB, CALIBRATION_MAX_DB)
        // try/catch instead of runCatching because runCatching swallows
        // CancellationException — turning a scope/lifecycle cancellation into a regular
        // Result.failure breaks structured cancellation and would surface a bogus
        // "save failed" snackbar at teardown. We rethrow cancellation explicitly and only
        // wrap genuine persistence failures in Result.failure.
        return try {
            repository.updateCalibrationOffset(rounded)
            Result.success(rounded)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}
