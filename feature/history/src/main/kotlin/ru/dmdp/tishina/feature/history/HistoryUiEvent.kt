package ru.dmdp.tishina.feature.history

/**
 * User intents on the History screen. The ViewModel maps each one to a state mutation +
 * optional one-shot effect (Snackbar etc.).
 */
sealed interface HistoryUiEvent {
    /** Triggered when a card is swiped past the dismiss threshold (FR-11). */
    data class DeleteRequested(val id: Long) : HistoryUiEvent

    /** Triggered by the "Undo" action on the snackbar, within 5 s of soft-delete. */
    data object UndoConfirmed : HistoryUiEvent
}
