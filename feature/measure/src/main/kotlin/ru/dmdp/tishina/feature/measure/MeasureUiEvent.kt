package ru.dmdp.tishina.feature.measure

/**
 * Intents the UI raises against `MeasureViewModel`. Modelled as a sealed
 * interface so adding new events (e.g. weighting toggle in Phase 4) is a
 * compile-time check across `MeasureViewModel.onEvent`.
 */
sealed interface MeasureUiEvent {
    data object StartRequested : MeasureUiEvent
    data object PauseRequested : MeasureUiEvent
    data object ResetRequested : MeasureUiEvent
    data object SaveRequested : MeasureUiEvent

    /**
     * Result of an in-flight system permission request.
     *
     * @param granted true iff RECORD_AUDIO is now granted.
     * @param shouldShowRationale value reported by
     *  `ActivityCompat.shouldShowRequestPermissionRationale` after the request.
     *  When `granted == false`, the (false, false) pair means the user picked
     *  "Don't ask again" — we transition to `PermanentlyDenied` and route to
     *  app settings.
     */
    data class PermissionResult(val granted: Boolean, val shouldShowRationale: Boolean) : MeasureUiEvent

    /**
     * Passive permission re-check on screen entry / resume. Unlike [PermissionResult]
     * this does NOT trigger `startCollecting` even when granted — the user must still
     * tap Start. Used to recover from "user denied twice → opened Settings → granted"
     * so the UI doesn't stay stuck on `PermanentlyDenied`.
     */
    data class PermissionRefreshed(val granted: Boolean) : MeasureUiEvent
}
