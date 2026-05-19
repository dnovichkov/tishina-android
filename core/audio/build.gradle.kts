plugins {
    alias(libs.plugins.tishina.android.library)
    alias(libs.plugins.tishina.jvm.testing)
}

android {
    namespace = "ru.dmdp.tishina.core.audio"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
}
