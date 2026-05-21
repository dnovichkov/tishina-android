plugins {
    alias(libs.plugins.tishina.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "ru.dmdp.tishina.feature.settings"

    testOptions {
        // Roborazzi/Robolectric needs access to packaged Android resources (string lookups,
        // drawables, styles). Without this, `stringResource(...)` returns empty strings under
        // unit tests and screenshot baselines diverge from the on-device render.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // `Icons.AutoMirrored.Outlined.ArrowBack` and Material symbols used by SettingsScreen.
    implementation(libs.androidx.compose.material.icons.extended)

    // `collectAsStateWithLifecycle` ships in lifecycle-runtime-compose (separate from -ktx).
    implementation(libs.androidx.lifecycle.runtime.compose)
}
