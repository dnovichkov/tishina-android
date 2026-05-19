package ru.dmdp.tishina.core.audio.dsp

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("AWeightingFilter — only supports the sample rates Phase 2 audio source emits")
class AWeightingFilterIllegalSampleRateTest {

    @ParameterizedTest(name = "{0} Hz must be rejected (only 44 100 and 48 000 are supported)")
    @ValueSource(ints = [-1, 0, 8_000, 16_000, 22_050, 32_000, 96_000, 192_000])
    fun `unsupported sample rate is rejected at construction`(sampleRateHz: Int) {
        assertThrows(IllegalArgumentException::class.java) {
            AWeightingFilter(sampleRateHz)
        }
    }

    @Test
    fun `supported sample rates construct successfully`() {
        assertDoesNotThrow { AWeightingFilter(44_100) }
        assertDoesNotThrow { AWeightingFilter(48_000) }
    }
}
