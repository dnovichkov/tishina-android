package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RingBuffer — fixed-capacity FloatArray ring with stats helpers")
class RingBufferTest {

    @Test
    fun `snapshot returns added values in chronological order when not yet full`() {
        val buffer = RingBuffer(capacity = 4)
        buffer.add(1f)
        buffer.add(2f)
        buffer.add(3f)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), buffer.snapshot())
    }

    @Test
    fun `snapshot wraps around when capacity is exceeded keeping newest values`() {
        val buffer = RingBuffer(capacity = 4)
        listOf(1f, 2f, 3f, 4f, 5f).forEach(buffer::add)
        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f), buffer.snapshot())
    }

    @Test
    fun `snapshot survives multiple full wraps`() {
        val buffer = RingBuffer(capacity = 3)
        (1..10).forEach { buffer.add(it.toFloat()) }
        // last 3 values added: 8, 9, 10
        assertArrayEquals(floatArrayOf(8f, 9f, 10f), buffer.snapshot())
    }

    @Test
    fun `mean of empty buffer is zero (no division by zero)`() {
        val buffer = RingBuffer(capacity = 4)
        assertEquals(0f, buffer.mean(), 0f)
    }

    @Test
    fun `mean reflects all added values when not full`() {
        val buffer = RingBuffer(capacity = 8)
        listOf(10f, 20f, 30f, 40f).forEach(buffer::add)
        assertEquals(25f, buffer.mean(), 1e-6f)
    }

    @Test
    fun `mean ignores values overwritten by wrap-around`() {
        val buffer = RingBuffer(capacity = 4)
        listOf(1f, 1f, 1f, 1f, 100f, 100f, 100f, 100f).forEach(buffer::add)
        assertEquals(100f, buffer.mean(), 1e-6f)
    }

    @Test
    fun `sumOfSquares of empty buffer is zero`() {
        val buffer = RingBuffer(capacity = 4)
        assertEquals(0f, buffer.sumOfSquares(), 0f)
    }

    @Test
    fun `sumOfSquares of 3 and 4 equals 25`() {
        val buffer = RingBuffer(capacity = 4)
        buffer.add(3f)
        buffer.add(4f)
        assertEquals(25f, buffer.sumOfSquares(), 1e-6f)
    }

    @Test
    fun `sumOfSquares accumulates using Kahan-stable summation for large counts`() {
        // Adding 1.0e6 copies of 1.0f naively in Float32 loses precision well before reaching 1e6;
        // with Kahan compensation we stay within a tight bound.
        val capacity = 1_000_000
        val buffer = RingBuffer(capacity = capacity)
        repeat(capacity) { buffer.add(1f) }
        assertEquals(capacity.toFloat(), buffer.sumOfSquares(), 1f)
    }

    @Test
    fun `negative or zero capacity is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { RingBuffer(capacity = 0) }
        assertThrows(IllegalArgumentException::class.java) { RingBuffer(capacity = -1) }
    }

    @Test
    fun `snapshot returns a defensive copy that callers can mutate`() {
        val buffer = RingBuffer(capacity = 4)
        buffer.add(1f)
        buffer.add(2f)
        val snapshot = buffer.snapshot()
        snapshot[0] = 999f
        assertArrayEquals(floatArrayOf(1f, 2f), buffer.snapshot())
    }

    @Test
    fun `size reports added count before fill and capacity once full`() {
        val buffer = RingBuffer(capacity = 3)
        assertEquals(0, buffer.size)
        buffer.add(1f)
        assertEquals(1, buffer.size)
        buffer.add(2f)
        buffer.add(3f)
        assertEquals(3, buffer.size)
        buffer.add(4f) // wrap-around — still 3
        assertEquals(3, buffer.size)
    }
}
