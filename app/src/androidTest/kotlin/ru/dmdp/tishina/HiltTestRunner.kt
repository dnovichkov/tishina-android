package ru.dmdp.tishina

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom [AndroidJUnitRunner] that swaps the production [TishinaApplication]
 * (annotated with `@HiltAndroidApp`) for Hilt's [HiltTestApplication] in
 * instrumentation tests.
 *
 * Without this swap, every `@HiltAndroidTest` would boot the real DI graph —
 * which pulls Room, DataStore and the production `MeasurementRepository` —
 * making it impossible to install `@TestInstallIn` replacements before the
 * Application class wires the singleton component.
 *
 * Phase 6 Task 9 — declared as `testInstrumentationRunner` in
 * `app/build.gradle.kts`; the `androidx.test.runner.AndroidJUnitRunner`
 * default would otherwise instantiate `TishinaApplication`.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
