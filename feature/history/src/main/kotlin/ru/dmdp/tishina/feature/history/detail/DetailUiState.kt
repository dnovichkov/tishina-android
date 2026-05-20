package ru.dmdp.tishina.feature.history.detail

import androidx.annotation.StringRes
import ru.dmdp.tishina.core.domain.model.MeasurementDetails

/**
 * Screen state for [DetailScreen].
 *
 *  * [details] — loaded measurement; `null` while loading or after a failure that triggered NavigateBack.
 *  * [loading] — `true` only on the initial load. Subsequent edit/delete operations show optimistic UI
 *    rather than a spinner, because the user is actively interacting and a flicker would be jarring.
 *  * [editingNote] — whether the note Card is in TextField (edit) mode vs. plain Text (read) mode.
 *  * [noteDraft] — the working copy of the note used by the TextField. Mirrors `details.summary.note`
 *    when not editing; modified only via [DetailUiEvent.NoteChanged].
 *  * [error] — string-resource id of the most recent transient error (note-too-long, not-found).
 *  * [deleteConfirmVisible] — drives the destructive AlertDialog. Kept in state (not UI-local
 *    `remember`) so the dialog survives configuration changes during confirmation.
 */
data class DetailUiState(
    val details: MeasurementDetails? = null,
    val loading: Boolean = true,
    val editingNote: Boolean = false,
    val noteDraft: String = "",
    @StringRes val error: Int? = null,
    val deleteConfirmVisible: Boolean = false,
)
