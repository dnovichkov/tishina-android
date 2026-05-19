plugins {
    alias(libs.plugins.tishina.android.library)
    alias(libs.plugins.tishina.android.compose)
    alias(libs.plugins.tishina.jvm.testing)
}

android {
    namespace = "ru.dmdp.tishina.core.testing"
}

dependencies {
    // PreviewSheet wraps TishinaTheme; expose as api so consumers don't double-declare it.
    api(projects.core.designsystem)

    // Fakes reference domain interfaces (AudioRepository, SoundSample) — must be on the
    // compile classpath of every consumer of :core:testing.
    api(projects.core.domain)

    // FakePcmAudioSource implements the PcmAudioSource interface declared in :core:audio.
    // Exposing audio as `api` lets :core:audio's test sources reuse the fake without
    // re-declaring the dep. The project graph is :core:audio.main ← :core:testing.main ←
    // :core:audio.test — directed, no cycle.
    api(projects.core.audio)

    // Test toolchain — api so a single testImplementation on this module pulls in the whole stack.
    api(libs.junit.jupiter.api)
    api(libs.junit.jupiter.params)
    api(libs.junit.jupiter.engine)
    api(libs.junit4)
    api(libs.junit.vintage.engine)
    api(libs.mockk)
    api(libs.turbine)
    api(libs.kotlinx.coroutines.test)
    api(libs.robolectric)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.roborazzi.junit.rule)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.test.junit4)
}
