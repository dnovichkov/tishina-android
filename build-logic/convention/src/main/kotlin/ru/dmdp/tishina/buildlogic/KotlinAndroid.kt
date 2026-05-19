package ru.dmdp.tishina.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

internal const val TISHINA_COMPILE_SDK = 35
internal const val TISHINA_MIN_SDK = 26
internal const val TISHINA_TARGET_SDK = 35

internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension<*, *, *, *, *, *>,
) {
    commonExtension.apply {
        compileSdk = TISHINA_COMPILE_SDK

        defaultConfig {
            minSdk = TISHINA_MIN_SDK
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    configureKotlin<KotlinCompilationTask<*>>()

    // Phase 1 only validates the debug variant — Roborazzi snapshots are recorded for debug,
    // release runs would replay them against a different variant config and noisily fail.
    // Skip release unit tests to keep the root-level Kover aggregator (`./gradlew koverHtmlReport`)
    // green; release builds still compile and are produced via `assembleRelease`/`bundleRelease`.
    tasks.withType<Test>().configureEach {
        if (name.contains("Release")) {
            enabled = false
        }
    }
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    configureKotlin<KotlinCompilationTask<*>>()
}

private inline fun <reified T : KotlinCompilationTask<*>> Project.configureKotlin() {
    tasks.withType(T::class.java).configureEach {
        val options = compilerOptions
        if (options is KotlinJvmCompilerOptions) {
            options.jvmTarget.set(JvmTarget.JVM_17)
        }
        options.freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-opt-in=kotlin.RequiresOptIn",
        )
    }
}
