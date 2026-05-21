plugins {
    alias(libs.plugins.tishina.android.library)
    // Hilt convention plugin already applies KSP — no separate `ksp` alias needed.
    alias(libs.plugins.tishina.android.hilt)
    alias(libs.plugins.tishina.jvm.testing)
}

android {
    namespace = "ru.dmdp.tishina.core.data"

    defaultConfig {
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    sourceSets {
        // MigrationTestHelper reads `<canonicalName>/<version>.json` from `assets`. For
        // Robolectric-based unit tests we expose the exported schemas under test assets;
        // androidTest would normally pick the same directory up for real instrumentation.
        named("test") {
            assets.srcDirs("$projectDir/schemas")
        }
        named("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Phase 4 — DataStore-backed SettingsRepositoryImpl persists calibration, theme,
    // dynamic colors and locale. Lives in `:core:data` so Hilt can share the singleton
    // file with the rest of the data layer (Room DB + Settings under one storage root).
    implementation(libs.datastore.preferences)

    testImplementation(projects.core.testing)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    // Robolectric ships JUnit 4 tests; vintage engine bridges them onto the JUnit 5 platform.
    testRuntimeOnly(libs.junit.vintage.engine)
}
