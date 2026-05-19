package ru.dmdp.tishina.core.audio.dsp

import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.MeasurementConfig

/**
 * Builds a fresh [AudioProcessor] tuned for a specific microphone [sampleRateHz] and a chosen
 * [MeasurementConfig]. Lives in Hilt's `SingletonComponent` (Phase 2 wires this via `@Provides`
 * in `AudioModule`) — a single factory instance is shared, but each measurement gets its own
 * [AudioProcessor] so per-session state never leaks between sessions.
 *
 * Choices are deliberate:
 * - [DcBlockFilter] uses its default pole (≈ 38 Hz HPF at 48 kHz) — adequate for all bands of
 *   interest in IEC 61672-1 A/Z weightings.
 * - [AWeightingFilter] is built per sample rate (only 44 100 and 48 000 are supported today);
 *   [ZWeightingFilter] is a no-op pass-through.
 * - [TimeWeightedRms] derives `tau` from [MeasurementConfig.timeWeighting].
 * - [SplCalculator] uses the AOSP-anchored default reference; calibration offset is applied
 *   later inside [AudioProcessor.process].
 */
class AudioProcessorFactory {

    /** Build a brand-new [AudioProcessor] for one measurement session. */
    fun create(sampleRateHz: Int, config: MeasurementConfig): AudioProcessor {
        val weighting: FrequencyFilter = when (config.frequencyWeighting) {
            FrequencyWeighting.A -> AWeightingFilter(sampleRateHz)
            FrequencyWeighting.Z -> ZWeightingFilter()
        }
        return AudioProcessor(
            dcBlock = DcBlockFilter(),
            weighting = weighting,
            timeWeightedRms = TimeWeightedRms(sampleRateHz, tauMs = config.timeWeighting.tauMs),
            spl = SplCalculator(),
        )
    }
}
