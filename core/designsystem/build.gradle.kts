plugins {
    alias(libs.plugins.tishina.android.library)
    alias(libs.plugins.tishina.android.compose)
    alias(libs.plugins.tishina.jvm.testing)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "ru.dmdp.tishina.core.designsystem"
}

dependencies {
    // TishinaTheme accepts ThemeMode from core:domain so the FR-17 enum stays a
    // single source of truth (UI + persistence both read the same type).
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.material.icons.extended)

    // Robolectric (JUnit 4) bridged onto the JUnit 5 platform via the vintage engine.
    // Roborazzi captures Compose composables under Robolectric without an emulator.
    testImplementation(projects.core.testing)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testRuntimeOnly(libs.junit.vintage.engine)
}
