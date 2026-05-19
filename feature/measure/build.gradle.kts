plugins {
    alias(libs.plugins.tishina.android.feature)
}

android {
    namespace = "ru.dmdp.tishina.feature.measure"
}

dependencies {
    implementation(projects.core.audio)
}
