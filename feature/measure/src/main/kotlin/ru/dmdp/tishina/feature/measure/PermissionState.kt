package ru.dmdp.tishina.feature.measure

/**
 * Tracks the RECORD_AUDIO permission decision for the current session.
 *
 * `Unknown` means we have not asked yet (cold start) — tapping FAB triggers a
 * request. `PermanentlyDenied` is signalled by the platform when the user has
 * checked "Don't ask again"; in that case we must send them to system settings
 * (see `MeasureUiEffect.OpenAppSettings`) since a fresh `requestPermission`
 * call would silently no-op.
 */
enum class PermissionState {
    Unknown,
    Granted,
    Denied,
    PermanentlyDenied,
}
