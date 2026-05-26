plugins {
    // `com.android.test` ships inside AGP — applying via libs.plugins alias
    // double-declares the version against the classpath added by the
    // tishina.android.* convention plugins, which fails with
    // "plugin already on the classpath with an unknown version".
    id("com.android.test")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.tishina.quality)
}

/**
 * Phase 6 Task 9 — `:macrobenchmark` is a separate `com.android.test` module
 * (not `application`, not `library`) because Macrobenchmark profiles a target
 * app from outside its process. Cold-start, frame-timing and baseline-profile
 * measurements would all be wrong if the benchmark ran inside the same VM as
 * the app under test.
 *
 * **Variant matching** — AGP resolves cross-module dependencies by build-type
 * name. `:app` exposes `debug` + `release`; we publish `benchmark` and fall
 * back to `release` so `:app:assembleRelease` is what gets profiled. That
 * gives us the R8-shrunk APK the production user actually installs, matching
 * the NFR-1 budget against the real shipping shape.
 *
 * **`suppressErrors`** — on a CI emulator we permit `EMULATOR`,
 * `LOW-BATTERY`, and (for the matching-release fallback that isn't
 * `profileable`) `NOT-PROFILEABLE`. These would make the benchmark fail
 * hard on a real Pixel 6a (the NFR target), which is desirable: the proxy
 * value is captured on CI, the precise number on production hardware.
 */
android {
    namespace = "ru.dmdp.tishina.macrobenchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] =
            "EMULATOR,LOW-BATTERY,NOT-PROFILEABLE,DEBUGGABLE"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // Default `debug` is created implicitly by `com.android.test`; we
        // disable it below in `beforeVariants` so AGP doesn't try to match it
        // against a non-existent `:app:debug` test variant.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    beforeVariants { variant ->
        // Only the `benchmark` variant is meaningful — the implicit `debug`
        // build type does not match `:app`'s `release` and would print
        // "could not find matching variant" warnings on every Gradle sync.
        variant.enable = variant.buildType == "benchmark"
    }
}
