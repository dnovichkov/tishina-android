import io.gitlab.arturbosch.detekt.Detekt

plugins {
    `kotlin-dsl`
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
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

    detektPlugins(libs.detekt.formatting)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// The convention layer is the source of truth for every other module's quality stack — it
// must itself be subject to the same static analysis (Detekt + Spotless+Ktlint) we apply
// downstream. Without this, a convention plugin can drift in style or skip a contract
// without any CI gate noticing.
detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = false
    parallel = true
    ignoreFailures = false
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
    }
}

tasks.register("detektAll", Detekt::class.java) {
    description = "Run Detekt across build-logic convention plugin sources."
    group = "verification"
    config.setFrom(rootProject.file("../config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    setSource(files("src"))
    include("**/*.kt", "**/*.kts")
    exclude("**/build/**", "**/generated/**", "**/resources/**")
    parallel = true
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                "android" to "true",
                "max_line_length" to "140",
                "ktlint_standard_filename" to "disabled",
                "ktlint_standard_multiline-expression-wrapping" to "disabled",
                "ktlint_standard_function-signature" to "disabled",
                "ktlint_standard_argument-list-wrapping" to "disabled",
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                "ktlint_standard_property-naming" to "disabled",
            ),
        )
    }
    kotlinGradle {
        // Cover the convention subproject's own *.gradle.kts AND the included build's
        // settings.gradle.kts one level up. The root project's Spotless cannot reach inside
        // build-logic/ (separate Gradle build), so without the `../*.gradle.kts` glob the
        // build-logic/settings.gradle.kts file would have no formatting gate.
        target("*.gradle.kts", "../*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf(
                "max_line_length" to "140",
                "ktlint_standard_filename" to "disabled",
                "ktlint_standard_multiline-expression-wrapping" to "disabled",
                "ktlint_standard_function-signature" to "disabled",
                "ktlint_standard_argument-list-wrapping" to "disabled",
            ),
        )
    }
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
