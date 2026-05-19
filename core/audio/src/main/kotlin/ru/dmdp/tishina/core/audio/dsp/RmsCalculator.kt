package ru.dmdp.tishina.core.audio.dsp

import kotlin.math.sqrt

/**
 * Sliding-window root-mean-square accumulator over the latest [windowSize] samples.
 *
 * Internally backed by a [RingBuffer] whose [RingBuffer.sumOfSquares] uses Kahan-stable
 * summation — that matters here because Phase 2 windows can reach 48 000 samples (1 s Slow),
 * where naive Float32 accumulation of unit-amplitude signals would drift noticeably.
 *
 * For the first samples before the ring is full the divisor is the current count, so the
 * RMS smoothly grows in from the cold start instead of being biased toward zero.
 */
class RmsCalculator(private val windowSize: Int) {

    init {
        require(windowSize > 0) { "windowSize must be positive, was $windowSize" }
    }

    private var buffer = RingBuffer(windowSize)

    /** Adds [sample] to the sliding window and returns the new RMS over the window. */
    fun update(sample: Float): Float {
        buffer.add(sample)
        val n = buffer.size
        if (n == 0) return 0f
        val ss = buffer.sumOfSquares()
        return sqrt(ss / n)
    }

    fun reset() {
        buffer = RingBuffer(windowSize)
    }
}
