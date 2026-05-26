package ru.dmdp.tishina.feature.history.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.dmdp.tishina.core.domain.model.MeasurementDetails
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementByIdUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateMeasurementNoteUseCase
import ru.dmdp.tishina.core.ui.snapshot.LineChartSnapshotter
import ru.dmdp.tishina.feature.history.R
import ru.dmdp.tishina.feature.history.detail.share.ShareIntentBuilder
import javax.inject.Inject

/**
 * ViewModel for [DetailScreen]. Owns the single-measurement view: load by id, inline-edit
 * note (FR-10), delete with confirmation (FR-11).
 *
 * Note-edit semantics:
 *  - `StartEditingNote` flips the UI into TextField mode but does NOT mutate the persisted value.
 *  - `NoteChanged` updates [DetailUiState.noteDraft]; the underlying [details] stays unchanged
 *    so a config-change-mid-edit still shows the persisted text when the screen rebinds.
 *  - `SaveNote` validates ≤ 200 chars (via [UpdateMeasurementNoteUseCase] returning Result), then
 *    optimistically updates [details] so the read-mode card reflects the new value before the
 *    Room Flow emission catches up.
 *  - `CancelEditingNote` resets [DetailUiState.noteDraft] to the persisted value and exits edit mode.
 *
 * Delete semantics: two-step (request → confirm) so accidental taps in the TopAppBar trash icon
 * don't destroy a measurement. The confirm dialog is rendered from state ([deleteConfirmVisible])
 * rather than UI-local `remember` so it survives configuration changes.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMeasurementById: GetMeasurementByIdUseCase,
    private val updateNote: UpdateMeasurementNoteUseCase,
    private val deleteMeasurement: DeleteMeasurementUseCase,
    private val snapshotter: LineChartSnapshotter,
    private val shareIntentBuilder: ShareIntentBuilder,
) : ViewModel() {

    private val measurementId: Long = savedStateHandle.toRoute<DetailRoute>().measurementId

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val effectChannel = Channel<DetailUiEffect>(Channel.BUFFERED)
    val effects: Flow<DetailUiEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Catch Exception (not Throwable) so a Room IO error or mapper failure during cold
            // start doesn't leave the screen pinned at loading=true forever. Both "not found"
            // (null) and "load failed" (Exception) route to the same UX: snackbar + NavigateBack —
            // the user has nothing useful to do on a broken Detail screen.
            //
            // CancellationException must propagate untouched: if viewModelScope is cancelled (the
            // user backs out while Room is still streaming), swallowing it would let the post-load
            // effect emissions race a cancelled scope. This matches HistoryViewModel.commitDeleteInternal.
            val details: MeasurementDetails? = try {
                getMeasurementById(measurementId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (details == null) {
                effectChannel.send(DetailUiEffect.ShowSnackbar(R.string.detail_not_found))
                effectChannel.send(DetailUiEffect.NavigateBack)
            } else {
                _state.update {
                    it.copy(
                        details = details,
                        noteDraft = details.summary.note.orEmpty(),
                        loading = false,
                    )
                }
            }
        }
    }

    fun onEvent(event: DetailUiEvent) {
        when (event) {
            DetailUiEvent.StartEditingNote -> startEditing()
            is DetailUiEvent.NoteChanged -> _state.update { it.copy(noteDraft = event.value) }
            DetailUiEvent.SaveNote -> saveNote()
            DetailUiEvent.CancelEditingNote -> cancelEditing()
            DetailUiEvent.DeleteRequested -> _state.update { it.copy(deleteConfirmVisible = true) }
            DetailUiEvent.DeleteConfirmed -> performDelete()
            DetailUiEvent.DeleteCancelled -> _state.update { it.copy(deleteConfirmVisible = false) }
            DetailUiEvent.ShareRequested -> performShare()
        }
    }

    private fun performShare() {
        // Tap before load completes is a defensive no-op — the TopAppBar gates the Share button
        // on details != null but the cold-start race is still possible if the user is fast.
        val details = _state.value.details ?: return
        viewModelScope.launch {
            // Snapshot dimensions chosen to match a comfortable share preview on most messengers
            // (Telegram caps at 1280 px on the longer edge; we stay below to avoid recompression).
            val bitmapResult = snapshotter.snapshot(
                samples = details.samples,
                widthPx = SHARE_BITMAP_WIDTH_PX,
                heightPx = SHARE_BITMAP_HEIGHT_PX,
            )
            val intent = shareIntentBuilder.build(
                details = details,
                chartBitmap = bitmapResult.getOrNull(),
            )
            effectChannel.send(DetailUiEffect.LaunchShareIntent(intent))
        }
    }

    private fun startEditing() {
        val current = _state.value
        _state.value = current.copy(
            editingNote = true,
            noteDraft = current.details?.summary?.note.orEmpty(),
        )
    }

    private fun cancelEditing() {
        val current = _state.value
        _state.value = current.copy(
            editingNote = false,
            noteDraft = current.details?.summary?.note.orEmpty(),
        )
    }

    private fun saveNote() {
        val current = _state.value
        val draft = current.noteDraft
        if (draft.length > NewMeasurement.MAX_NOTE_LENGTH) {
            effectChannel.trySend(DetailUiEffect.ShowSnackbar(R.string.detail_note_too_long))
            // Stay in edit mode so the user can shorten the text.
            return
        }
        // Whitespace-only draft → null persisted note. `isNotBlank` (not `isNotEmpty`) so a
        // draft of `"   "` doesn't survive as a note while `NoteReadOnly` blanks it out on
        // re-render, leaving an invisible-but-truthy value in Room.
        val normalized: String? = draft.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val result = updateNote(measurementId, normalized)
            result.fold(
                onSuccess = {
                    _state.update { state ->
                        val details = state.details ?: return@update state
                        state.copy(
                            details = details.copy(
                                summary = details.summary.copy(note = normalized),
                            ),
                            noteDraft = normalized.orEmpty(),
                            editingNote = false,
                        )
                    }
                },
                onFailure = { error ->
                    // The in-VM length guard above catches the > 200 case before we reach the
                    // use-case; any failure that lands here is therefore from a deeper layer
                    // (Room IO, FK constraint, dispatcher cancellation). Show a generic message
                    // rather than misleading the user with "note too long".
                    val messageRes = if (error is IllegalArgumentException) {
                        R.string.detail_note_too_long
                    } else {
                        R.string.detail_note_save_failed
                    }
                    effectChannel.send(DetailUiEffect.ShowSnackbar(messageRes))
                },
            )
        }
    }

    private fun performDelete() {
        viewModelScope.launch {
            _state.update { it.copy(deleteConfirmVisible = false) }
            // Catch Exception (not Throwable) so a Room IO failure doesn't tear down the
            // viewModelScope job AND leave the user pinned on Detail with the confirm dialog
            // already closed. On failure we stay on the screen (so they can retry or pick
            // another action) and surface a snackbar; success path is unchanged — pop back to
            // History.
            //
            // CancellationException is rethrown so structured cancellation propagates: if the
            // user navigates away mid-delete, we don't want a spurious detail_delete_failed
            // snackbar emitted on the way out. Mirrors HistoryViewModel.commitDeleteInternal.
            val failure: Throwable? = try {
                deleteMeasurement(measurementId)
                null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Exception) {
                throwable
            }
            if (failure == null) {
                effectChannel.send(DetailUiEffect.NavigateBack)
            } else {
                effectChannel.send(DetailUiEffect.ShowSnackbar(R.string.detail_delete_failed))
            }
        }
    }

    private companion object {
        // Share-Intent PNG dimensions. Big enough to look sharp in messengers, small enough
        // to stay well under WhatsApp's 16 MB total payload limit even uncompressed.
        const val SHARE_BITMAP_WIDTH_PX = 1080
        const val SHARE_BITMAP_HEIGHT_PX = 540
    }
}
