package ru.dmdp.tishina.feature.history.detail

/**
 * UI intents fired by [DetailScreen]. Kept as a sealed interface so the ViewModel's
 * `when (event)` is exhaustive — new events break the compile.
 */
sealed interface DetailUiEvent {

    /** User tapped the read-only note area → switch to TextField. */
    data object StartEditingNote : DetailUiEvent

    /** TextField content changed; update [DetailUiState.noteDraft]. */
    data class NoteChanged(val value: String) : DetailUiEvent

    /** "Save" tapped in edit mode → validate + persist + leave edit mode. */
    data object SaveNote : DetailUiEvent

    /** "Cancel" tapped in edit mode → revert [DetailUiState.noteDraft] to persisted value. */
    data object CancelEditingNote : DetailUiEvent

    /** Trash icon in TopAppBar → show confirmation AlertDialog. */
    data object DeleteRequested : DetailUiEvent

    /** "Delete" in confirmation AlertDialog → delete + navigate back. */
    data object DeleteConfirmed : DetailUiEvent

    /** "Cancel" in confirmation AlertDialog → close dialog without deleting. */
    data object DeleteCancelled : DetailUiEvent

    /** Share icon in TopAppBar → render PNG snapshot and open system chooser (FR-10 P1). */
    data object ShareRequested : DetailUiEvent
}
