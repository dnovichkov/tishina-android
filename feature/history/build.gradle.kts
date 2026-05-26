plugins {
    alias(libs.plugins.tishina.android.feature)
    alias(libs.plugins.roborazzi)
    // Detail route declares `@Serializable data class DetailRoute(val measurementId: Long)` so
    // `SavedStateHandle.toRoute<DetailRoute>()` in DetailViewModel can recover the id without
    // string keys. The serialization plugin generates the companion serializer at compile time.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.dmdp.tishina.feature.history"

    testOptions {
        // Roborazzi/Robolectric needs access to packaged Android resources (string lookups,
        // drawables, styles). Without this, `stringResource(...)` returns empty strings under
        // unit tests and screenshot baselines diverge from the on-device render.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // Phase 6 Task 3 — the FR-20 CSV exporter implementation (`MeasurementsExporterImpl`)
    // lives in `:core:data`; the history DI module binds it to the `:core:domain` interface.
    implementation(projects.core.data)

    // `Icons.Default.Delete` and other Material Symbols for the swipe-delete UI.
    implementation(libs.androidx.compose.material.icons.extended)

    // `collectAsStateWithLifecycle` ships in lifecycle-runtime-compose (separate from -ktx).
    implementation(libs.androidx.lifecycle.runtime.compose)

    // `androidx.navigation.compose.composable<T>` and `SavedStateHandle.toRoute<T>()` —
    // pulled here so DetailRoute / DetailViewModel can use type-safe arguments without
    // requiring the :app module to declare the route.
    implementation(libs.androidx.navigation.compose)

    // Backing format for `@Serializable` route descriptors.
    implementation(libs.kotlinx.serialization.json)
}
