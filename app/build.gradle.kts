plugins {
    alias(libs.plugins.tishina.android.application)
    alias(libs.plugins.tishina.android.compose)
    alias(libs.plugins.tishina.android.hilt)
    alias(libs.plugins.tishina.jvm.testing)
    alias(libs.plugins.tishina.oss.licenses)
    alias(libs.plugins.kotlin.serialization)
}

// Phase 6 Task 6 — release signing wired through environment variables so the
// upload key never lands on disk in plaintext. `release.yml` decodes the
// base64-encoded keystore from a GitHub Secret onto the runner just before
// `bundleRelease`; locally these variables are unset, so we transparently fall
// back to the debug key (still produces a runnable APK for R8 validation).
val uploadKeystorePath: String? = System.getenv("UPLOAD_KEYSTORE_PATH")
val uploadKeystorePassword: String? = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
val uploadKeyAlias: String? = System.getenv("UPLOAD_KEY_ALIAS")
val uploadKeyPassword: String? = System.getenv("UPLOAD_KEY_PASSWORD")
val hasUploadKeystore = uploadKeystorePath != null &&
    uploadKeystorePassword != null &&
    uploadKeyAlias != null &&
    uploadKeyPassword != null &&
    file(uploadKeystorePath).exists()

android {
    namespace = "ru.dmdp.tishina"

    defaultConfig {
        applicationId = "ru.dmdp.tishina"
        // CI injects `VERSION_CODE` from `github.run_number` and `VERSION_NAME`
        // from the pushed git tag (`v1.2.3` -> `1.2.3`). Local builds fall back
        // to a static `-dev` marker so `versionName` is never empty.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        resourceConfigurations += listOf("ru", "en")
    }

    signingConfigs {
        if (hasUploadKeystore) {
            create("release") {
                storeFile = file(uploadKeystorePath!!)
                storePassword = uploadKeystorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Phase 6 Task 2 — R8 in full mode (AGP 8 default) plus resource
            // shrinking target NFR-4 (<= 6 MB release APK, <= 8 MB AAB).
            // Keep rules live in `proguard-rules.pro` plus per-module
            // `consumer-rules.pro` files bundled into AARs.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use the real upload key when env-vars + keystore are present
            // (CI release.yml flow); otherwise fall back to debug signing so
            // local R8 smoke-builds and the `release-build` CI job still work
            // without provisioning a keystore on every dev machine.
            signingConfig = if (hasUploadKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.audio)
    implementation(projects.feature.measure)
    implementation(projects.feature.history)
    implementation(projects.feature.settings)
    implementation(projects.feature.about)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // FR-18 — AppCompat 1.6+ exposes `AppCompatDelegate.setApplicationLocales` /
    // `LocaleManagerCompat`, which the LocaleSwitcher uses to apply per-app locales.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.splashscreen)
    implementation(libs.kotlinx.serialization.json)

    // Test stack: Robolectric is JUnit4; vintage engine bridges it onto the JUnit 5 platform
    // configured by JvmTestingConventionPlugin so a single `:testDebugUnitTest` task runs both.
    testImplementation(projects.core.testing)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
}
