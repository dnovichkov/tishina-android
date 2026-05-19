plugins {
    alias(libs.plugins.tishina.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "ru.dmdp.tishina.feature.measure"

    testOptions {
        // Roborazzi/Robolectric needs access to packaged Android resources (string lookups,
        // drawables, styles). Without this, `stringResource(...)` returns empty strings under
        // unit tests and screenshot baselines diverge from the on-device render.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(projects.core.audio)
    implementation(libs.androidx.compose.material.icons.extended)

    // `rememberLauncherForActivityResult` lives in activity-compose, not the convention plugin.
    implementation(libs.androidx.activity.compose)

    // `collectAsStateWithLifecycle` ships in lifecycle-runtime-compose (separate from -ktx).
    implementation(libs.androidx.lifecycle.runtime.compose)
}
