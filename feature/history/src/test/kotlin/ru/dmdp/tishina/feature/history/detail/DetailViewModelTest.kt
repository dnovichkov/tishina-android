package ru.dmdp.tishina.feature.history.detail

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementByIdUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateMeasurementNoteUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementRepository
import ru.dmdp.tishina.core.ui.snapshot.LineChartSnapshotter
import ru.dmdp.tishina.feature.history.R
import ru.dmdp.tishina.feature.history.detail.share.ShareIntentBuilder

/**
 * Behavior covered:
 *  - id is recovered from SavedStateHandle via type-safe [DetailRoute];
 *  - successful load fills [DetailUiState.details] + clears loading flag + initialises noteDraft;
 *  - missing id → ShowSnackbar(detail_not_found) + NavigateBack effects, no state crash;
 *  - StartEditingNote / NoteChanged / SaveNote happy-path with note ≤ 200;
 *  - SaveNote with note > 200 → ShowSnackbar(detail_note_too_long), useCase NOT called;
 *  - CancelEditingNote reverts noteDraft to the persisted value;
 *  - DeleteRequested → DeleteConfirmed → deletion + NavigateBack;
 *  - DeleteCancelled closes the dialog without deleting.
 *
 * Robolectric needed because `SavedStateHandle.toRoute<DetailRoute>()` internally calls
 * `bundleOf(...)` → `BaseBundle.putLong`, which throws "not mocked" under raw JVM stubs.
 * JUnit4 (vintage engine) lets us use `@RunWith(RobolectricTestRunner::class)` while
 * coexisting with the JUnit 5 platform.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stateHandleFor(id: Long): SavedStateHandle =
        SavedStateHandle(mapOf("measurementId" to id))

    private fun sampleNew(
        title: String? = "first",
        note: String? = "initial note",
        db: Float = 60f,
    ) = NewMeasurement(
        createdAtEpochMs = 1_000L,
        durationMs = 10_000L,
        avgDb = db,
        minDb = db - 5f,
        maxDb = db + 5f,
        title = title,
        note = note,
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 48_000,
        samples = listOf(SoundSample(db = db, timestampMs = 0L)),
    )

    private fun newViewModel(
        repo: FakeMeasurementRepository,
        id: Long,
    ): DetailViewModel = DetailViewModel(
        savedStateHandle = stateHandleFor(id),
        getMeasurementById = GetMeasurementByIdUseCase(repo),
        updateNote = UpdateMeasurementNoteUseCase(repo),
        deleteMeasurement = DeleteMeasurementUseCase(repo),
        snapshotter = LineChartSnapshotter(),
        shareIntentBuilder = ShareIntentBuilder(
            context = androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            cacheSubdir = "share-test",
            fileToUri = { file -> android.net.Uri.parse("content://test/${file.name}") },
        ),
    )

    @Test
    fun `id is recovered from SavedStateHandle via DetailRoute`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("measurementId" to 42L))
        val route = handle.toRoute<DetailRoute>()
        assertEquals(42L, route.measurementId)
    }

    @Test
    fun `successful load fills details, clears loading, seeds noteDraft`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew(title = "Спальня", note = "тихо"))).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        val loaded = vm.state.first { !it.loading }
        assertNotNull(loaded.details)
        assertEquals(id, loaded.details?.summary?.id)
        assertEquals("тихо", loaded.noteDraft)
        assertFalse(loaded.editingNote)
        assertNull(loaded.error)
    }

    @Test
    fun `missing id emits not-found snackbar then NavigateBack`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val vm = newViewModel(repo, id = 999L)

        vm.effects.test {
            val firstEffect = awaitItem()
            assertTrue(firstEffect is DetailUiEffect.ShowSnackbar)
            assertEquals(R.string.detail_not_found, (firstEffect as DetailUiEffect.ShowSnackbar).messageRes)
            val secondEffect = awaitItem()
            assertTrue(secondEffect is DetailUiEffect.NavigateBack)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `StartEditingNote enters edit mode`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew(note = "hi"))).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.onEvent(DetailUiEvent.StartEditingNote)
        val state = vm.state.first { it.editingNote }
        assertTrue(state.editingNote)
        assertEquals("hi", state.noteDraft) // draft seeded from persisted value
    }

    @Test
    fun `NoteChanged updates draft without persisting`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew(note = "initial"))).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.onEvent(DetailUiEvent.NoteChanged("typed"))
        runCurrent()
        val state = vm.state.value
        assertEquals("typed", state.noteDraft)
        // Repository note still holds the original.
        val persisted = repo.getById(id)?.summary?.note
        assertEquals("initial", persisted)
    }

    @Test
    fun `SaveNote within 200 chars persists, leaves edit mode, updates details`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew(note = "old"))).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.onEvent(DetailUiEvent.StartEditingNote)
        vm.onEvent(DetailUiEvent.NoteChanged("new note"))
        vm.onEvent(DetailUiEvent.SaveNote)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.editingNote)
        assertEquals("new note", state.details?.summary?.note)
        assertEquals("new note", repo.getById(id)?.summary?.note)
    }

    @Test
    fun `SaveNote with empty string clears note (null)`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew(note = "to be wiped"))).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.onEvent(DetailUiEvent.StartEditingNote)
        vm.onEvent(DetailUiEvent.NoteChanged(""))
        vm.onEvent(DetailUiEvent.SaveNote)
        advanceUntilIdle()

        assertEquals(null, repo.getById(id)?.summary?.note)
        assertEquals(null, vm.state.value.details?.summary?.note)
    }

    @Test
    fun `SaveNote over 200 chars emits note_too_long snackbar and does not persist`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew(note = "kept"))).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.effects.test {
            vm.onEvent(DetailUiEvent.StartEditingNote)
            vm.onEvent(DetailUiEvent.NoteChanged("x".repeat(201)))
            vm.onEvent(DetailUiEvent.SaveNote)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is DetailUiEffect.ShowSnackbar)
            assertEquals(R.string.detail_note_too_long, (effect as DetailUiEffect.ShowSnackbar).messageRes)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("note must not have been overwritten", "kept", repo.getById(id)?.summary?.note)
        assertTrue("user is still in edit mode to fix the input", vm.state.value.editingNote)
    }

    @Test
    fun `CancelEditingNote reverts draft to persisted value and leaves edit mode`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew(note = "persisted"))).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.onEvent(DetailUiEvent.StartEditingNote)
        vm.onEvent(DetailUiEvent.NoteChanged("ephemeral typing"))
        vm.onEvent(DetailUiEvent.CancelEditingNote)
        runCurrent()

        val state = vm.state.value
        assertFalse(state.editingNote)
        assertEquals("persisted", state.noteDraft)
    }

    @Test
    fun `DeleteRequested shows confirm dialog without deleting`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew())).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.onEvent(DetailUiEvent.DeleteRequested)
        runCurrent()
        assertTrue(vm.state.value.deleteConfirmVisible)
        assertEquals("delete must wait for explicit confirmation", 1, repo.size())
    }

    @Test
    fun `DeleteConfirmed deletes row and emits NavigateBack`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew())).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.effects.test {
            vm.onEvent(DetailUiEvent.DeleteRequested)
            vm.onEvent(DetailUiEvent.DeleteConfirmed)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is DetailUiEffect.NavigateBack)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, repo.size())
    }

    @Test
    fun `init wraps getMeasurementById throw and routes to not-found UX`() = runTest(testDispatcher) {
        // Regression guard: without runCatching, a Room IO failure during cold-start load
        // pinned the screen at loading=true and the user could only kill the app.
        val repo = object : FakeMeasurementRepository() {
            override suspend fun getById(id: Long): ru.dmdp.tishina.core.domain.model.MeasurementDetails? =
                error("simulated Room IO error")
        }
        val vm = newViewModel(repo, id = 1L)

        vm.effects.test {
            val firstEffect = awaitItem()
            assertTrue(firstEffect is DetailUiEffect.ShowSnackbar)
            assertEquals(R.string.detail_not_found, (firstEffect as DetailUiEffect.ShowSnackbar).messageRes)
            val secondEffect = awaitItem()
            assertTrue(secondEffect is DetailUiEffect.NavigateBack)
            cancelAndIgnoreRemainingEvents()
        }
        // We routed the user away, so the screen never received any details to render.
        // Crucially, no state mutation should have happened during the failed load.
        assertNull("no details should be loaded on IO failure", vm.state.value.details)
        assertEquals("noteDraft stays empty on IO failure", "", vm.state.value.noteDraft)
    }

    @Test
    fun `SaveNote with repository failure shows save_failed snackbar not too_long`() = runTest(testDispatcher) {
        // The in-VM length guard catches > 200 chars before the use-case runs; failures here
        // mean a deeper problem (Room write IO error, FK constraint, etc.). The user should
        // see "couldn't save" rather than the misleading "note too long".
        val repo = object : FakeMeasurementRepository() {
            override suspend fun updateNote(id: Long, note: String?): Unit =
                error("simulated disk full")
        }
        val id = repo.seed(listOf(sampleNew(note = "initial"))).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.effects.test {
            vm.onEvent(DetailUiEvent.StartEditingNote)
            vm.onEvent(DetailUiEvent.NoteChanged("a short note"))
            vm.onEvent(DetailUiEvent.SaveNote)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is DetailUiEffect.ShowSnackbar)
            assertEquals(
                R.string.detail_note_save_failed,
                (effect as DetailUiEffect.ShowSnackbar).messageRes,
            )
            cancelAndIgnoreRemainingEvents()
        }
        // User stays in edit mode so they can retry without re-typing.
        assertTrue("retain edit mode to allow retry", vm.state.value.editingNote)
    }

    @Test
    fun `DeleteCancelled hides confirm dialog without deleting`() = runTest(testDispatcher) {
        val repo = FakeMeasurementRepository()
        val id = repo.seed(listOf(sampleNew())).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.onEvent(DetailUiEvent.DeleteRequested)
        vm.onEvent(DetailUiEvent.DeleteCancelled)
        runCurrent()

        assertFalse(vm.state.value.deleteConfirmVisible)
        assertEquals(1, repo.size())
    }

    @Test
    fun `DeleteConfirmed with repository failure shows delete_failed snackbar and stays`() = runTest(testDispatcher) {
        // Regression: without runCatching the IO failure took down the viewModelScope job AFTER
        // the confirm dialog had already been dismissed by performDelete, so the user was
        // left on a Detail screen with no feedback and no way to recover except killing the app.
        val repo = object : FakeMeasurementRepository() {
            override suspend fun delete(id: Long): Unit = error("simulated Room IO error")
        }
        val id = repo.seed(listOf(sampleNew())).single()
        val vm = newViewModel(repo, id)
        runCurrent()

        vm.effects.test {
            vm.onEvent(DetailUiEvent.DeleteRequested)
            vm.onEvent(DetailUiEvent.DeleteConfirmed)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue("expected ShowSnackbar(delete_failed) on failure", effect is DetailUiEffect.ShowSnackbar)
            assertEquals(
                R.string.detail_delete_failed,
                (effect as DetailUiEffect.ShowSnackbar).messageRes,
            )
            cancelAndIgnoreRemainingEvents()
        }
        // Confirm dialog stays closed (we dismissed it before attempting delete), the row is
        // still on disk because the repository threw, and no NavigateBack effect fired.
        assertFalse("confirm dialog should remain hidden after the failed attempt", vm.state.value.deleteConfirmVisible)
        assertEquals("row must still exist after a failed delete", 1, repo.size())
    }
}
