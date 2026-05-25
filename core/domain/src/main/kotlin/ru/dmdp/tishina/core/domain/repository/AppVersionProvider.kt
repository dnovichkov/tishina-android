package ru.dmdp.tishina.core.domain.repository

import ru.dmdp.tishina.core.domain.model.AppVersion

/**
 * Resolves the currently-installed [AppVersion] at runtime (FR-21 AboutScreen header).
 *
 * The interface lives in `:core:domain` so `AboutViewModel` / future settings rows can
 * inject it without coupling to Android's `BuildConfig`. The concrete implementation
 * lives in `:core:data` and reads via `PackageManager.getPackageInfo` — that path is
 * stable across API 26+ and is exactly what survives an APK split, unlike a hardcoded
 * `BuildConfig.VERSION_NAME` read which is generated per-module and can drift.
 *
 * Marked `suspend` so the implementation can hop onto an IO dispatcher — the underlying
 * `PackageManager` call is an IPC round-trip and StrictMode flags it as a disk-blocking
 * operation on cold launches. Implementations must never throw: a `NameNotFoundException`
 * on the app's own package is degenerate, so the contract is "always returns a valid
 * [AppVersion]" (defaults to empty name + 0 code on the impossible failure path).
 */
fun interface AppVersionProvider {
    suspend fun get(): AppVersion
}
