package ru.dmdp.tishina.feature.history.detail

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementByIdUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateMeasurementNoteUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementRepository
import ru.dmdp.tishina.core.ui.snapshot.LineChartSnapshotter
import ru.dmdp.tishina.feature.history.detail.share.ShareIntentBuilder

/**
 * Phase 6 Task 4 — TDD contract for [DetailViewModel]'s ShareRequested → LaunchShareIntent
 * flow (FR-10 P1).
 *
 * Behaviour:
 *  - `ShareRequested` triggers an off-screen chart snapshot via [LineChartSnapshotter],
 *    then passes the resulting Bitmap (or null on failure) to [ShareIntentBuilder].
 *  - The resulting `Intent` is published as a one-shot `LaunchShareIntent` effect.
 *  - Snapshotter failure flips the builder into text-only mode — no separate VM error
 *    handling needed; the builder owns the fallback policy.
 *  - Multiple sequential ShareRequested events each produce one effect (no swallowing).
 *
 * GraphicsMode.NATIVE is required because the snapshotter dispatches a real
 * `Bitmap.createBitmap` + `Canvas.drawPath`. Under LEGACY mode the bitmap would have zero
 * pixels and we'd accidentally drop into the text-only branch even when the production
 * device would render fine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class DetailViewModelShareTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleNew(
        title: String? = "Bedroom",
        sampleCount: Int = 30,
    ) = NewMeasurement(
        createdAtEpochMs = 1_700_000_000_000L,
        durationMs = 60_000L,
        avgDb = 60f,
        minDb = 50f,
        maxDb = 70f,
        title = title,
        note = null,
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 48_000,
        samples = (0 until sampleCount).map { i ->
            SoundSample(db = 55f + (i % 10), timestampMs = (i * 200L))
        },
    )

    private fun fakeShareIntentBuilder(): ShareIntentBuilder = ShareIntentBuilder(
        context = context,
        cacheSubdir = "share-test",
        fileToUri = { file -> Uri.parse("content://test/${file.name}") },
    )

    private fun stateHandle(id: Long): SavedStateHandle =
        SavedStateHandle(mapOf("measurementId" to id))

    private fun newViewModel(
        repo: FakeMeasurementRepository,
        id: Long,
        snapshotter: LineChartSnapshotter = LineChartSnapshotter(),
        shareIntentBuilder: ShareIntentBuilder = fakeShareIntentBuilder(),
    ): DetailViewModel = DetailViewModel(
        savedStateHandle = stateHandle(id),
        getMeasurementById = GetMeasurementByIdUseCase(repo),
        updateNote = UpdateMeasurementNoteUseCase(repo),
        deleteMeasurement = DeleteMeasurementUseCase(repo),
        snapshotter = snapshotter,
        shareIntentBuilder = shareIntentBuilder,
    )

    @Test
    fun `ShareRequested emits LaunchShareIntent with image_png intent`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew(title = "Bedroom"))).single()
        val vm = newViewModel(repo, id)
        // wait for the load
        runCurrent()
        vm.state.first { !it.loading }

        vm.effects.test {
            vm.onEvent(DetailUiEvent.ShareRequested)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue("expected LaunchShareIntent, got $effect", effect is DetailUiEffect.LaunchShareIntent)
            val intent = (effect as DetailUiEffect.LaunchShareIntent).intent
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals(
                "rich snapshot path should attach a PNG when LineChartSnapshotter succeeds",
                "image/png",
                intent.type,
            )
            val streamUri = androidx.core.content.IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )
            assertNotNull("EXTRA_STREAM must carry the PNG URI on the rich path", streamUri)
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            assertNotNull(text)
            assertTrue("share text should mention the measurement title", text!!.contains("Bedroom"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `snapshotter failure falls back to text_plain intent`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew(title = "Office"))).single()
        val failingSnapshotter = object : LineChartSnapshotter() {
            override suspend fun snapshot(
                samples: List<SoundSample>,
                widthPx: Int,
                heightPx: Int,
            ): Result<Bitmap> = Result.failure(IllegalStateException("simulated render failure"))
        }
        val vm = newViewModel(repo, id, snapshotter = failingSnapshotter)
        runCurrent()
        vm.state.first { !it.loading }

        vm.effects.test {
            vm.onEvent(DetailUiEvent.ShareRequested)
            advanceUntilIdle()
            val intent = (awaitItem() as DetailUiEffect.LaunchShareIntent).intent
            // Text-only fallback when snapshot failed — see ShareIntentBuilder contract.
            assertEquals("text/plain", intent.type)
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            assertNotNull(text)
            assertTrue(text!!.contains("Office"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ShareRequested before load is a no-op`() = runTest(testDispatcher) {
        // If the user somehow taps Share before `details` is loaded (race with cold-start),
        // we must not crash on `state.details!!`. The VM should silently swallow the event;
        // the TopAppBar action is only enabled once details are loaded but defense-in-depth.
        val repo = FakeMeasurementRepository().apply {
            // Seed but make getById slow — ShareRequested fires before init coroutine resolves.
        }
        val id = repo.seed(listOf(sampleNew())).single()
        val vm = newViewModel(repo, id)

        vm.effects.test {
            vm.onEvent(DetailUiEvent.ShareRequested)
            // Don't advance — init coroutine hasn't loaded details yet.
            runCurrent()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `subsequent ShareRequested fires another LaunchShareIntent`() = runTest(testDispatcher) {
        // Regression guard: if we cached the bitmap or de-duped the effect emission, the user
        // would lose the second tap after returning from the chooser. Each ShareRequested is
        // a distinct one-shot effect.
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew())).single()
        val vm = newViewModel(repo, id)
        runCurrent()
        vm.state.first { !it.loading }

        vm.effects.test {
            vm.onEvent(DetailUiEvent.ShareRequested)
            advanceUntilIdle()
            awaitItem()
            vm.onEvent(DetailUiEvent.ShareRequested)
            advanceUntilIdle()
            val second = awaitItem()
            assertTrue(second is DetailUiEffect.LaunchShareIntent)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
