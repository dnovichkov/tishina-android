plugins {
    alias(libs.plugins.tishina.android.library)
    alias(libs.plugins.tishina.android.compose)
    alias(libs.plugins.tishina.jvm.testing)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "ru.dmdp.tishina.core.ui"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(projects.core.designsystem)
    // `SplLineChart` consumes `SoundSample` from the domain layer. Domain stays the lowest
    // shared dependency: features depend on :core:ui which depends on :core:domain, never
    // feature → feature.
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testRuntimeOnly(libs.junit.vintage.engine)
    // PreviewSheet wraps screenshot tests in the standard TishinaTheme frame — using the
    // same wrapper across modules keeps baselines pixel-comparable when components migrate.
    testImplementation(projects.core.testing)
}
