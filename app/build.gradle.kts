plugins {
    alias(libs.plugins.tishina.android.application)
    alias(libs.plugins.tishina.android.compose)
    alias(libs.plugins.tishina.android.hilt)
    alias(libs.plugins.tishina.jvm.testing)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.dmdp.tishina"

    defaultConfig {
        applicationId = "ru.dmdp.tishina"
        versionCode = 1
        versionName = "0.1.0-foundation"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        resourceConfigurations += listOf("ru", "en")
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
            // Phase 6 Task 6 will replace this with the real upload-key signing
            // config wired through GitHub Secrets. The debug key here lets us
            // run `assembleRelease`/`bundleRelease` locally to validate R8
            // output without provisioning a keystore on every dev machine.
            signingConfig = signingConfigs.getByName("debug")
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
