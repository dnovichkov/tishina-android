package ru.dmdp.tishina.core.domain.usecase

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.dmdp.tishina.core.domain.model.MeasurementConfig
import ru.dmdp.tishina.core.domain.model.SessionSeed
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.repository.AudioRepository

@DisplayName("StartMeasurementUseCase — accumulator over Flow<SoundSample>")
class StartMeasurementUseCaseTest {

    private val config = MeasurementConfig()

    @Test
    fun `accumulates min, max, avg, and current across a sequence`() = runTest {
        val samples = listOf(
            SoundSample(db = 40f, timestampMs = 0L),
            SoundSample(db = 60f, timestampMs = 100L),
            SoundSample(db = 80f, timestampMs = 200L),
            SoundSample(db = 60f, timestampMs = 300L),
            SoundSample(db = 40f, timestampMs = 400L),
        )
        val repo = mockk<AudioRepository>()
        every { repo.samples(config) } returns flowOf(*samples.toTypedArray())

        val useCase = StartMeasurementUseCase(repo)

        useCase(config).test {
            // 5 snapshots, one per sample, no initial seed
            repeat(4) { awaitItem() }
            val final = awaitItem()
            assertEquals(40f, final.currentDb)
            assertEquals(40f, final.minDb)
            assertEquals(80f, final.maxDb)
            assertEquals(56f, final.avgDb, 0.001f)
            assertEquals(400L, final.durationMs)
            awaitComplete()
        }
    }

    @Test
    fun `first emitted snapshot mirrors the first sample (no empty seed leak)`() = runTest {
        val repo = mockk<AudioRepository>()
        every { repo.samples(config) } returns flowOf(SoundSample(db = 73f, timestampMs = 0L))

        val useCase = StartMeasurementUseCase(repo)

        useCase(config).test {
            val first = awaitItem()
            assertEquals(73f, first.currentDb)
            assertEquals(73f, first.minDb)
            assertEquals(73f, first.maxDb)
            assertEquals(73f, first.avgDb, 0.001f)
            awaitComplete()
        }
    }

    @Test
    fun `recent window is trimmed to the last 60 seconds`() = runTest {
        // 13 samples spaced 10s apart: timestamps 0, 10_000, ..., 120_000.
        // At the final sample, cutoff = 120_000 - 60_000 = 60_000 → samples with
        // timestampMs >= 60_000 survive: that's t=60k, 70k, 80k, 90k, 100k, 110k, 120k = 7 samples.
        val samples = (0..12).map { i ->
            SoundSample(db = 50f + i, timestampMs = i.toLong() * 10_000L)
        }
        val repo = mockk<AudioRepository>()
        every { repo.samples(config) } returns flowOf(*samples.toTypedArray())

        val useCase = StartMeasurementUseCase(repo)

        val snapshots = mutableListOf<ru.dmdp.tishina.core.domain.model.MeasurementSnapshot>()
        useCase(config).test {
            repeat(13) { snapshots += awaitItem() }
            awaitComplete()
        }
        val final = snapshots.last()
        assertEquals(7, final.recent.size)
        assertEquals(60_000L, final.recent.first().timestampMs)
        assertEquals(120_000L, final.recent.last().timestampMs)
    }

    @Test
    fun `recent retains everything when the session is shorter than 60 seconds`() = runTest {
        val samples = (0..5).map { i ->
            SoundSample(db = 60f, timestampMs = i.toLong() * 100L)
        }
        val repo = mockk<AudioRepository>()
        every { repo.samples(config) } returns flowOf(*samples.toTypedArray())

        val useCase = StartMeasurementUseCase(repo)

        useCase(config).test {
            repeat(5) { awaitItem() }
            val final = awaitItem()
            assertEquals(6, final.recent.size)
            awaitComplete()
        }
    }

    @Test
    fun `empty input flow does not emit a snapshot`() = runTest {
        val repo = mockk<AudioRepository>()
        every { repo.samples(config) } returns emptyFlow()

        val useCase = StartMeasurementUseCase(repo)

        useCase(config).test {
            // No items expected; the flow simply completes.
            awaitComplete()
        }
    }

    @Test
    fun `seeded session continues min, max, avg, and duration across the upstream restart`() = runTest {
        // Seed represents the state from a pre-pause session: min=40, max=80, avg=60 over 3 samples
        // summing to 180, with the clock at 200 ms. A single new sample at 60 dB on a re-zeroed
        // upstream must (a) keep min at 40, (b) keep max at 80, (c) compute avg = 240/4 = 60, and
        // (d) emit durationMs = 200 + 0 = 200 (timestamp shifted into session clock).
        val seed = SessionSeed(
            minDb = 40f,
            maxDb = 80f,
            sumDb = 180.0,
            count = 3L,
            durationOffsetMs = 200L,
            recent = emptyList(),
        )
        val repo = mockk<AudioRepository>()
        every { repo.samples(config) } returns flowOf(SoundSample(db = 60f, timestampMs = 0L))

        val useCase = StartMeasurementUseCase(repo)

        useCase(config, seed).test {
            val snapshot = awaitItem()
            assertEquals(60f, snapshot.currentDb)
            assertEquals(40f, snapshot.minDb)
            assertEquals(80f, snapshot.maxDb)
            assertEquals(60f, snapshot.avgDb, 0.001f)
            assertEquals(200L, snapshot.durationMs)
            awaitComplete()
        }
    }

    @Test
    fun `passes the config through to the audio repository verbatim`() = runTest {
        val repo = mockk<AudioRepository>()
        val customConfig = config.copy(calibrationOffsetDb = 5f)
        every { repo.samples(customConfig) } returns emptyFlow()

        val useCase = StartMeasurementUseCase(repo)

        useCase(customConfig).test { awaitComplete() }
        // mockk would throw on un-stubbed config — assertion is implicit via call match.
        assertTrue(true)
    }
}
