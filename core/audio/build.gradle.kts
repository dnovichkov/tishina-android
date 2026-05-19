plugins {
    alias(libs.plugins.tishina.android.library)
    alias(libs.plugins.tishina.android.hilt)
    alias(libs.plugins.tishina.jvm.testing)
}

android {
    namespace = "ru.dmdp.tishina.core.audio"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    // Robolectric runs on JUnit 4; vintage engine bridges its tests onto the JUnit 5 platform.
    testRuntimeOnly(libs.junit.vintage.engine)
}
