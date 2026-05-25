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
 * Implementations must be safe to call from any dispatcher and must never throw —
 * a missing PackageInfo is a programmer error (the app cannot be running without
 * one), so the contract is "always returns a valid [AppVersion]".
 */
fun interface AppVersionProvider {
    fun get(): AppVersion
}
