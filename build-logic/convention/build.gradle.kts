plugins {
    `kotlin-dsl`
}

group = "ru.dmdp.tishina.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.plugin.android.gradle)
    compileOnly(libs.plugin.kotlin.gradle)
    compileOnly(libs.plugin.kotlin.compose)
    compileOnly(libs.plugin.ksp.gradle)
    compileOnly(libs.plugin.hilt.gradle)
    compileOnly(libs.plugin.detekt.gradle)
    compileOnly(libs.plugin.spotless.gradle)
    compileOnly(libs.plugin.kover.gradle)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "tishina.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "tishina.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "tishina.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidCompose") {
            id = "tishina.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "tishina.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "tishina.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        register("jvmTesting") {
            id = "tishina.jvm.testing"
            implementationClass = "JvmTestingConventionPlugin"
        }
        register("quality") {
            id = "tishina.quality"
            implementationClass = "QualityConventionPlugin"
        }
    }
}
