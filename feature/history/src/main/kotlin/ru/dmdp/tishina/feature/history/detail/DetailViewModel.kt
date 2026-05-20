package ru.dmdp.tishina.feature.history.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.dmdp.tishina.core.domain.model.NewMeasurement
import ru.dmdp.tishina.core.domain.usecase.DeleteMeasurementUseCase
import ru.dmdp.tishina.core.domain.usecase.GetMeasurementByIdUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateMeasurementNoteUseCase
import ru.dmdp.tishina.feature.history.R
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
) : ViewModel() {

    private val measurementId: Long = savedStateHandle.toRoute<DetailRoute>().measurementId

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val effectChannel = Channel<DetailUiEffect>(Channel.BUFFERED)
    val effects: Flow<DetailUiEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val details = getMeasurementById(measurementId)
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
        // Empty TextField input → null persisted note (NULL in Room, hides the note card in detail).
        val normalized: String? = draft.takeIf { it.isNotEmpty() }
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
                onFailure = {
                    effectChannel.send(DetailUiEffect.ShowSnackbar(R.string.detail_note_too_long))
                },
            )
        }
    }

    private fun performDelete() {
        viewModelScope.launch {
            _state.update { it.copy(deleteConfirmVisible = false) }
            deleteMeasurement(measurementId)
            effectChannel.send(DetailUiEffect.NavigateBack)
        }
    }
}
