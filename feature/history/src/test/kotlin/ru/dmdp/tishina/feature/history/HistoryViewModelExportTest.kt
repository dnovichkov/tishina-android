package ru.dmdp.tishina.feature.history

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import ru.dmdp.tishina.core.domain.model.ExportFilter
import ru.dmdp.tishina.core.domain.model.FrequencyWeighting
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.model.SoundSample
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.domain.repository.MeasurementsExporter
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementsUseCase
import ru.dmdp.tishina.core.domain.usecase.ExportHistoryUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementsUseCase
import ru.dmdp.tishina.core.testing.fakes.FakeMeasurementRepository
import ru.dmdp.tishina.core.testing.rules.MainDispatcherRule
import java.util.concurrent.atomic.AtomicInteger

/**
 * FR-20 CSV export from the ViewModel side.
 *
 * Contract:
 *  - [HistoryUiEvent.ExportRequested] (with optional `ExportFilter`) emits a
 *    [HistoryUiEffect.LaunchSafPicker] effect carrying a suggested filename — the screen
 *    captures the system SAF picker via `rememberLauncherForActivityResult`.
 *  - [HistoryUiEvent.ExportFileSelected] (URI returned by the picker) invokes the
 *    [ExportHistoryUseCase] and emits a success or failure snackbar effect.
 *  - SAF picker cancellation surfaces as [HistoryUiEvent.ExportCancelled] (or the
 *    screen simply does not dispatch ExportFileSelected — both paths are exercised).
 *
 * Suggested-filename strategy: `tishina-history-YYYY-MM-DD.csv` based on the system
 * clock. Tests inject a fixed clock so the assertion is deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelExportTest {

    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherRule(StandardTestDispatcher())

    private fun sampleMeasurement(createdAt: Long, title: String? = null): NewMeasurement = NewMeasurement(
        createdAtEpochMs = createdAt,
        durationMs = 10_000L,
        avgDb = 60f,
        minDb = 55f,
        maxDb = 65f,
        title = title,
        note = null,
        weighting = FrequencyWeighting.A,
        timeWeighting = TimeWeighting.FAST,
        calibrationOffsetDb = 0f,
        sampleRateHz = 48_000,
        samples = listOf(SoundSample(db = 60f, timestampMs = 0L)),
    )

    private class RecordingExporter(private val response: Result<Int> = Result.success(0)) : MeasurementsExporter {
        val calls = AtomicInteger(0)
        var lastTarget: String? = null
        var lastFilter: ExportFilter? = null

        override suspend fun export(targetUriString: String, filter: ExportFilter): Result<Int> {
            calls.incrementAndGet()
            lastTarget = targetUriString
            lastFilter = filter
            return response
        }
    }

    private fun viewModel(
        repo: FakeMeasurementRepository,
        exporter: MeasurementsExporter = RecordingExporter(),
        // A fixed instant — 2026-05-25 00:00 UTC — keeps the suggested-filename assertion stable.
        nowMillisProvider: () -> Long = { FIXED_NOW_MILLIS },
    ): HistoryViewModel = HistoryViewModel(
        getMeasurements = GetMeasurementsUseCase(repo),
        deleteMeasurement = DeleteMeasurementUseCase(repo),
        deleteMeasurements = DeleteMeasurementsUseCase(repo),
        exportHistory = ExportHistoryUseCase(exporter),
        nowMillisProvider = nowMillisProvider,
    )

    @Test
    fun `ExportRequested emits LaunchSafPicker effect with dated suggested filename`() = runTest {
        val repo = FakeMeasurementRepository().apply {
            seed(listOf(sampleMeasurement(createdAt = 1_000L, title = "x")))
        }
        val vm = viewModel(repo)

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.ExportRequested(ExportFilter.All))
            runCurrent()
            val effect = awaitItem()
            assertTrue(effect is HistoryUiEffect.LaunchSafPicker, "expected LaunchSafPicker, got $effect")
            val picker = effect as HistoryUiEffect.LaunchSafPicker
            // Format: tishina-history-YYYY-MM-DD.csv — UTC date for the fixed clock.
            assertEquals("tishina-history-$FIXED_NOW_DATE.csv", picker.suggestedName)
            assertEquals(ExportFilter.All, picker.filter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ExportRequested with ByIds carries the filter through the LaunchSafPicker effect`() = runTest {
        val repo = FakeMeasurementRepository().apply {
            seed(listOf(sampleMeasurement(createdAt = 1_000L)))
        }
        val vm = viewModel(repo)

        vm.effects.test {
            val filter = ExportFilter.ByIds(setOf(1L, 2L))
            vm.onEvent(HistoryUiEvent.ExportRequested(filter))
            runCurrent()
            val effect = awaitItem() as HistoryUiEffect.LaunchSafPicker
            assertEquals(filter, effect.filter)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ExportFileSelected invokes exporter and emits success snackbar with row count`() = runTest {
        val repo = FakeMeasurementRepository().apply {
            seed(listOf(sampleMeasurement(createdAt = 1_000L), sampleMeasurement(createdAt = 2_000L)))
        }
        val exporter = RecordingExporter(response = Result.success(2))
        val vm = viewModel(repo, exporter)

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.ExportFileSelected("content://docs/myfile.csv", ExportFilter.All))
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(
                effect is HistoryUiEffect.ShowExportSuccessSnackbar,
                "expected ShowExportSuccessSnackbar, got $effect",
            )
            assertEquals(2, (effect as HistoryUiEffect.ShowExportSuccessSnackbar).rowCount)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, exporter.calls.get())
        assertEquals("content://docs/myfile.csv", exporter.lastTarget)
        assertEquals(ExportFilter.All, exporter.lastFilter)
    }

    @Test
    fun `ExportFileSelected with ByIds forwards filter and snackbar count matches use-case result`() = runTest {
        val repo = FakeMeasurementRepository()
        val exporter = RecordingExporter(response = Result.success(1))
        val vm = viewModel(repo, exporter)

        vm.effects.test {
            vm.onEvent(
                HistoryUiEvent.ExportFileSelected(
                    targetUriString = "content://docs/sub.csv",
                    filter = ExportFilter.ByIds(setOf(7L)),
                ),
            )
            advanceUntilIdle()
            val effect = awaitItem() as HistoryUiEffect.ShowExportSuccessSnackbar
            assertEquals(1, effect.rowCount)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(ExportFilter.ByIds(setOf(7L)), exporter.lastFilter)
    }

    @Test
    fun `ExportFileSelected with empty result yields success snackbar with zero rows`() = runTest {
        val repo = FakeMeasurementRepository()
        val exporter = RecordingExporter(response = Result.success(0))
        val vm = viewModel(repo, exporter)

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.ExportFileSelected("content://docs/empty.csv", ExportFilter.All))
            advanceUntilIdle()
            val effect = awaitItem() as HistoryUiEffect.ShowExportSuccessSnackbar
            assertEquals(0, effect.rowCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exporter failure emits ShowExportFailedSnackbar effect`() = runTest {
        val repo = FakeMeasurementRepository()
        val exporter = RecordingExporter(response = Result.failure(RuntimeException("disk full")))
        val vm = viewModel(repo, exporter)

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.ExportFileSelected("content://docs/bad.csv", ExportFilter.All))
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(
                effect is HistoryUiEffect.ShowExportFailedSnackbar,
                "expected ShowExportFailedSnackbar, got $effect",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ExportCancelled is a no-op - no effects no exporter call`() = runTest {
        val repo = FakeMeasurementRepository().apply {
            seed(listOf(sampleMeasurement(createdAt = 1_000L)))
        }
        val exporter = RecordingExporter()
        val vm = viewModel(repo, exporter)

        vm.effects.test {
            vm.onEvent(HistoryUiEvent.ExportCancelled)
            runCurrent()
            // Should not emit anything.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, exporter.calls.get())
    }

    @Test
    fun `state passes through ExportRequested without altering selection mode or items`() = runTest {
        val repo = FakeMeasurementRepository().apply {
            seed(listOf(sampleMeasurement(createdAt = 1_000L, title = "k")))
        }
        val vm = viewModel(repo)

        vm.state.test {
            // initialState (loading=true)
            awaitItem()
            // first real emission
            val initial = awaitItem()
            assertTrue(initial.items.isNotEmpty())

            vm.onEvent(HistoryUiEvent.ExportRequested(ExportFilter.All))
            runCurrent()
            // ExportRequested must not produce a state change (no selection mutation,
            // no filter, no loading flag). The list remains identical.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        // 2026-05-25T00:00:00Z — matches docs/plans/2026-05-25-tishina-release.md date.
        const val FIXED_NOW_MILLIS: Long = 1_779_667_200_000L
        const val FIXED_NOW_DATE: String = "2026-05-25"
    }
}
