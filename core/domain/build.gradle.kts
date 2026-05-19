plugins {
    alias(libs.plugins.tishina.kotlin.library)
    alias(libs.plugins.tishina.jvm.testing)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
