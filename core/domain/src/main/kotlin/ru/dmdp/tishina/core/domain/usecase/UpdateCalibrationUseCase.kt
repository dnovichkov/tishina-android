package ru.dmdp.tishina.core.domain.usecase

import ru.dmdp.tishina.core.domain.model.AppearanceSettings.Companion.CALIBRATION_MAX_DB
import ru.dmdp.tishina.core.domain.model.AppearanceSettings.Companion.CALIBRATION_MIN_DB
import ru.dmdp.tishina.core.domain.model.AppearanceSettings.Companion.CALIBRATION_STEP_DB
import ru.dmdp.tishina.core.domain.repository.SettingsRepository
import kotlin.math.roundToInt

/**
 * FR-14 — validates the calibration offset proposed by the UI (range, NaN,
 * infinity), snaps it to the [CALIBRATION_STEP_DB] grid and persists it through
 * [SettingsRepository]. Returns the rounded value on success or wraps any
 * thrown error in [Result.failure].
 */
class UpdateCalibrationUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(db: Float): Result<Float> = runCatching {
        require(db.isFinite()) { "Calibration offset must be a finite number, was $db" }
        require(db in CALIBRATION_MIN_DB..CALIBRATION_MAX_DB) {
            "Calibration offset $db out of range [$CALIBRATION_MIN_DB, $CALIBRATION_MAX_DB]"
        }
        val rounded = (db / CALIBRATION_STEP_DB).roundToInt() * CALIBRATION_STEP_DB
        repository.updateCalibrationOffset(rounded)
        rounded
    }
}
