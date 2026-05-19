// Top-level build file — plugin aliases are declared here without `apply` so that
// they are resolved by Gradle's plugin marker mechanism, then applied in module
// `build.gradle.kts` files (or in convention plugins under `build-logic/`).
//
// Kover is applied at the root because the root project acts as the coverage aggregator:
// it pulls in per-module coverage (`kover(projects.*)`) and produces unified HTML/XML reports
// under `build/reports/kover/`.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
}

// Root-level Spotless covers the root `build.gradle.kts` and `settings.gradle.kts` — module-level
// Spotless (via `tishina.quality`) handles each subproject's own *.kt and *.gradle.kts files.
spotless {
    kotlinGradle {
        target("*.gradle.kts")
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

dependencies {
    kover(projects.app)
    kover(projects.core.designsystem)
    kover(projects.core.ui)
    kover(projects.core.domain)
    kover(projects.core.data)
    kover(projects.core.audio)
    kover(projects.feature.measure)
    kover(projects.feature.history)
    kover(projects.feature.settings)
    kover(projects.feature.about)
    // `:core:testing` intentionally excluded — measuring coverage of test-helper code
    // distorts aggregate numbers without surfacing meaningful gaps.
}

// Aggregate task that fans out to every real module's `detektAll`. Intermediate container projects
// (`:core`, `:feature`) get auto-created by `include(":core:foo")` but have no build script — we
// filter them out so the dependency list contains only modules where Detekt is actually applied.
tasks.register("detektAll") {
    group = "verification"
    description = "Runs Detekt on every module."
    dependsOn(
        subprojects
            .filter { it.buildFile.exists() }
            .map { "${it.path}:detektAll" },
    )
}

kover {
    reports {
        filters {
            excludes {
                // Framework/generated artifacts that contribute nothing actionable to coverage.
                // Class patterns are FQN-anchored to avoid silently swallowing legitimate Phase 2
                // code: `*MainActivity*` (unanchored) would also exclude future `MainActivityViewModel`,
                // and `*Application*` would exclude `ApplicationCoroutineScope` etc. — both of which
                // are exactly the kind of code we want measured.
                classes(
                    "*.BuildConfig",
                    "*.databinding.*",
                    "*_HiltModules*",
                    "*_Factory",
                    "*_Factory\$*",
                    "*_MembersInjector",
                    "*Hilt_*",
                    "ru.dmdp.tishina.MainActivity",
                    "ru.dmdp.tishina.TishinaApplication",
                    "*ComposableSingletons*",
                    "*\$\$serializer",
                )
                packages(
                    "hilt_aggregated_deps",
                    "dagger.hilt.internal.*",
                )
                annotatedBy(
                    "androidx.compose.ui.tooling.preview.Preview",
                )
            }
        }
        // Coverage thresholds are deliberately NOT enforced in Phase 1 — the plan only requires the
        // HTML/XML report to be generated. Thresholds (domain ≥ 90%, audio ≥ 95%, etc.) become
        // verifying rules in Phase 2 once real production code lands.
    }
}
