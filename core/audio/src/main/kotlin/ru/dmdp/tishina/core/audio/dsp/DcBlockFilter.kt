package ru.dmdp.tishina.core.audio.dsp

/**
 * Single-pole IIR high-pass filter that removes the DC bias microphones introduce.
 *
 * Transfer function: `y[n] = x[n] − x[n−1] + pole·y[n−1]`.
 * Default [pole] of 0.995 gives a −3 dB corner around 38 Hz at a 48 kHz sample rate, well below
 * the lowest A-weighted band of interest (31.5 Hz) yet aggressive enough to settle DC drift in
 * a few hundred samples.
 */
class DcBlockFilter(private val pole: Float = DEFAULT_POLE) {

    private var prevInput = 0f
    private var prevOutput = 0f

    /**
     * Processes [samples] through the filter. By default the operation is in-place; pass an
     * explicit [into] buffer (of equal size) to keep the source intact.
     */
    fun process(samples: FloatArray, into: FloatArray = samples) {
        require(samples.size == into.size) {
            "destination size ${into.size} must match samples size ${samples.size}"
        }
        var x1 = prevInput
        var y1 = prevOutput
        for (i in samples.indices) {
            val x = samples[i]
            val y = x - x1 + pole * y1
            into[i] = y
            x1 = x
            y1 = y
        }
        prevInput = x1
        prevOutput = y1
    }

    fun reset() {
        prevInput = 0f
        prevOutput = 0f
    }

    private companion object {
        const val DEFAULT_POLE = 0.995f
    }
}
