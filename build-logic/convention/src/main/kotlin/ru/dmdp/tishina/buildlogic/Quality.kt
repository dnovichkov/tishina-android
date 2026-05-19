package ru.dmdp.tishina.buildlogic

import com.android.build.api.dsl.Lint
import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Wires Detekt into the project: applies the plugin, points it at the shared config under
 * `<root>/config/detekt/detekt.yml`, and registers a per-module `detektAll` task that scans every
 * Kotlin source file under `src/` (skipping generated/build outputs).
 *
 * `buildUponDefaultConfig = true` means our YAML only needs to record overrides — packaged defaults
 * stay in effect for everything we don't mention.
 */
internal fun Project.configureDetekt() {
    pluginManager.apply("io.gitlab.arturbosch.detekt")

    val detektVersion = libs.findVersion("detekt").get().requiredVersion
    val detektConfig = rootProject.file("config/detekt/detekt.yml")

    extensions.configure<DetektExtension> {
        toolVersion = detektVersion
        config.setFrom(detektConfig)
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
            md.required.set(false)
            txt.required.set(false)
        }
    }

    tasks.register("detektAll", Detekt::class.java) {
        description = "Run Detekt on every Kotlin source set in this module (no Gradle-provided variants)."
        group = "verification"
        config.setFrom(detektConfig)
        buildUponDefaultConfig = true
        setSource(files("src"))
        include("**/*.kt", "**/*.kts")
        exclude("**/build/**", "**/generated/**", "**/resources/**", "**/build-logic/build/**")
        parallel = true
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
        }
    }

    dependencies {
        add("detektPlugins", libs.findLibrary("detekt-formatting").get())
    }
}

/**
 * Wires Spotless+Ktlint as the formatter. Detekt's own `formatting` ruleset is intentionally
 * disabled in `detekt.yml` so the two tools don't fight over style.
 *
 * `android = true` switches ktlint to AOSP-flavored rules; `max_line_length = 140` matches our
 * `.editorconfig` and Detekt's `MaxLineLength` so all three tools agree.
 */
internal fun Project.configureSpotless() {
    pluginManager.apply("com.diffplug.spotless")

    val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion

    extensions.configure<SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude(
                "**/build/**",
                "**/generated/**",
                "**/.gradle/**",
                "**/build-logic/build/**",
            )
            ktlint(ktlintVersion).editorConfigOverride(
                mapOf(
                    "android" to "true",
                    "max_line_length" to "140",
                    "ktlint_standard_filename" to "disabled",
                    // multiline-expression-wrapping fights with Compose's vertical DSL style; off for now.
                    "ktlint_standard_multiline-expression-wrapping" to "disabled",
                    // Compose composables often have function-signature line lengths that exceed
                    // the rule's preferred line wrapping; we keep the formatting decision to authors.
                    "ktlint_standard_function-signature" to "disabled",
                    // `argument-list-wrapping` repeatedly contradicts IDE auto-formatting for DSL builders.
                    "ktlint_standard_argument-list-wrapping" to "disabled",
                    // Composables are PascalCase by Compose convention; detekt's FunctionNaming
                    // already enforces the lowercase rule for non-@Composable functions.
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    // PascalCase top-level `const val` (test tags, CompositionLocal keys) is
                    // Compose-idiomatic; detekt's TopLevelPropertyNaming covers the same ground
                    // with a Compose-aware pattern, so we silence the duplicate ktlint check.
                    "ktlint_standard_property-naming" to "disabled",
                ),
            )
        }
        kotlinGradle {
            target("**/*.gradle.kts")
            targetExclude("**/build/**", "**/.gradle/**", "**/build-logic/build/**")
            ktlint(ktlintVersion).editorConfigOverride(
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
}

/**
 * Applies the Kover plugin for line/branch coverage collection. The root project aggregates
 * per-module reports into a single HTML/XML output (see root `build.gradle.kts`).
 *
 * Excluded classes (BuildConfig, Hilt-generated `*_HiltModules`, Application/MainActivity entry
 * points) don't contribute to meaningful coverage metrics and are filtered at the root level.
 */
internal fun Project.configureKover() {
    pluginManager.apply("org.jetbrains.kotlinx.kover")
}

/**
 * Configures Android Lint inside an existing Android module. Called from
 * [AndroidApplicationConventionPlugin] and [AndroidLibraryConventionPlugin]; not applicable to
 * pure-Kotlin modules where there are no Android resources to inspect.
 *
 * The baseline file (`lint-baseline.xml`) sits next to the module's build script. When it exists
 * it records pre-existing issues that should NOT block CI; new issues outside the baseline still
 * fail the build (`abortOnError = true`).
 */
internal fun configureAndroidLint(project: Project, lint: Lint) {
    val baselineFile = project.file("lint-baseline.xml")
    lint.apply {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        // Lint analysis of test sources triggers most of the K2-incompatibility crashes (see below).
        // Tests are still type-checked by the Kotlin compiler and exercised by `testDebugUnitTest`,
        // so skipping them in lint loses very little signal during Phase 1.
        checkTestSources = false
        ignoreTestSources = true
        if (baselineFile.exists()) {
            baseline = baselineFile
        }
        // Several androidx.compose.runtime + lifecycle lint detectors were rebuilt against the K2
        // Kotlin Analysis API, but AGP 8.7.3 bundles a UAST runtime that still uses the K1
        // implementation of those classes — leading to `Found class Ka<*>Call, but interface was
        // expected` IncompatibleClassChangeError crashes.
        // Tracking: https://issuetracker.google.com/issues/336842138.
        // Each detector listed here was empirically observed to crash; revisit once we upgrade to
        // AGP 8.8+ where the K2 UAST is the default.
        disable += setOf(
            "FlowOperatorInvokedInComposition",
            "FrequentlyChangedStateReadInComposition",
            "FrequentlyChangingValue",
            "RememberInComposition",
            "RememberReturnType",
            "UnrememberedMutableState",
            "AutoboxingStateCreation",
            "AutoboxingStateValueProperty",
            "CoroutineCreationDuringComposition",
            "ProduceStateDoesNotAssignValue",
            "ComposableNaming",
            "MutableCollectionMutableState",
            "NullSafeMutableLiveData",
        )
        sarifReport = true
        htmlReport = true
        xmlReport = true
    }
}
