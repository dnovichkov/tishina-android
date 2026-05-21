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
            isMinifyEnabled = false
            isShrinkResources = false
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
