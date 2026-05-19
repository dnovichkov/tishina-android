package ru.dmdp.tishina.core.audio.dsp

/**
 * Fixed-capacity ring of [Float] samples backed by a primitive [FloatArray].
 *
 * Primitive backing (vs `Array<Float>`) avoids autoboxing on the audio hot path where ~48 000
 * samples per second flow through the DSP pipeline. Reads via [snapshot] return values in
 * chronological order; statistics ([mean], [sumOfSquares]) are computed lazily on demand.
 */
class RingBuffer(private val capacity: Int) {

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    private val data = FloatArray(capacity)
    private var writeIndex = 0
    private var filled = false

    /** Number of valid samples currently held (≤ [capacity]). */
    val size: Int
        get() = if (filled) capacity else writeIndex

    fun add(value: Float) {
        data[writeIndex] = value
        writeIndex++
        if (writeIndex >= capacity) {
            writeIndex = 0
            filled = true
        }
    }

    /** Returns samples in chronological (oldest → newest) order as a defensive copy. */
    fun snapshot(): FloatArray {
        val n = size
        val out = FloatArray(n)
        if (!filled) {
            System.arraycopy(data, 0, out, 0, n)
        } else {
            val head = writeIndex
            val tail = capacity - head
            System.arraycopy(data, head, out, 0, tail)
            System.arraycopy(data, 0, out, tail, head)
        }
        return out
    }

    fun mean(): Float {
        val n = size
        if (n == 0) return 0f
        var sum = 0.0
        var compensation = 0.0
        for (i in 0 until n) {
            val raw = (if (filled) data[(writeIndex + i) % capacity] else data[i]).toDouble()
            val y = raw - compensation
            val t = sum + y
            compensation = (t - sum) - y
            sum = t
        }
        return (sum / n).toFloat()
    }

    fun sumOfSquares(): Float {
        val n = size
        if (n == 0) return 0f
        var sum = 0.0
        var compensation = 0.0
        for (i in 0 until n) {
            val v = (if (filled) data[(writeIndex + i) % capacity] else data[i]).toDouble()
            val y = v * v - compensation
            val t = sum + y
            compensation = (t - sum) - y
            sum = t
        }
        return sum.toFloat()
    }
}
