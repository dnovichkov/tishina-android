package ru.dmdp.tishina.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

class ModuleDependencyTest {

    private val repoRoot: File = File("../..").canonicalFile

    private val projectLinkRegex = Regex("""projects\.([a-zA-Z0-9.]+)""")
    private val featureLinkRegex = Regex("""projects\.feature\.([a-zA-Z0-9]+)""")

    @ParameterizedTest(name = "{0} only depends on declared modules from spec §7")
    @MethodSource("modulesWithAllowedDeps")
    fun moduleDependenciesMatchSpec(modulePath: String, allowed: Set<String>) {
        val buildFile = File(repoRoot, "$modulePath/build.gradle.kts")
        assertTrue(buildFile.isFile, "missing $buildFile")
        val text = buildFile.readText()

        val actual = projectLinkRegex.findAll(text)
            .map { it.groupValues[1] }
            .toSet()

        actual.forEach { dep ->
            assertTrue(
                allowed.contains(dep),
                "$modulePath depends on $dep, but only $allowed are allowed by spec §7",
            )
        }
    }

    @ParameterizedTest(name = "{0} declares required dependencies from spec §7")
    @MethodSource("modulesWithRequiredDeps")
    fun moduleDeclaresRequired(modulePath: String, required: Set<String>) {
        val buildFile = File(repoRoot, "$modulePath/build.gradle.kts")
        val text = buildFile.readText()
        val actual = projectLinkRegex.findAll(text).map { it.groupValues[1] }.toSet()
        required.forEach { dep ->
            assertTrue(actual.contains(dep), "$modulePath must depend on projects.$dep")
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("no feature module depends on another feature module")
    fun featuresDoNotDependOnFeatures() {
        val features = listOf("measure", "history", "settings", "about")
        features.forEach { feature ->
            val buildFile = File(repoRoot, "feature/$feature/build.gradle.kts")
            val text = buildFile.readText()
            val crossFeatureRefs = featureLinkRegex.findAll(text)
                .map { it.groupValues[1] }
                .filter { it != feature }
                .toList()
            assertEquals(
                emptyList<String>(),
                crossFeatureRefs,
                "feature:$feature cross-references other features: $crossFeatureRefs",
            )
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName(":app is the only sink — no other module depends on :app")
    fun nothingDependsOnApp() {
        val allOtherModules = listOf(
            "core/designsystem",
            "core/ui",
            "core/domain",
            "core/data",
            "core/audio",
            "core/testing",
            "feature/measure",
            "feature/history",
            "feature/settings",
            "feature/about",
        )
        allOtherModules.forEach { module ->
            val text = File(repoRoot, "$module/build.gradle.kts").readText()
            assertTrue(
                !text.contains("projects.app") && !text.contains("project(\":app\")"),
                "$module must not depend on :app — :app is the composition root",
            )
        }
    }

    companion object {
        @JvmStatic
        fun modulesWithAllowedDeps(): List<org.junit.jupiter.params.provider.Arguments> = listOf(
            args("core/domain", emptySet()),
            // :core:audio uses :core:testing only for test sources (FakePcmAudioSource).
            args("core/audio", setOf("core.domain", "core.testing")),
            args("core/data", setOf("core.domain")),
            args("core/designsystem", emptySet()),
            args("core/ui", setOf("core.designsystem")),
            // :core:testing reaches into :core:domain and :core:audio to expose Fakes for those
            // interfaces. The :core:audio.main ← :core:testing.main ← :core:audio.test edge is
            // directed (no cycle); see core/testing/build.gradle.kts for the rationale.
            args("core/testing", setOf("core.designsystem", "core.domain", "core.audio")),
            args(
                // Phase 2: :feature:measure now consumes :core:audio for the live engine.
                "feature/measure",
                setOf("core.designsystem", "core.ui", "core.domain", "core.testing", "core.audio"),
            ),
            args(
                "feature/history",
                setOf("core.designsystem", "core.ui", "core.domain", "core.testing"),
            ),
            args(
                "feature/settings",
                setOf("core.designsystem", "core.ui", "core.domain", "core.testing"),
            ),
            args(
                "feature/about",
                setOf("core.designsystem", "core.ui", "core.domain", "core.testing"),
            ),
            args(
                "app",
                setOf(
                    "core.designsystem",
                    "core.ui",
                    "core.domain",
                    "core.data",
                    "core.audio",
                    "feature.measure",
                    "feature.history",
                    "feature.settings",
                    "feature.about",
                ),
            ),
        )

        @JvmStatic
        fun modulesWithRequiredDeps(): List<org.junit.jupiter.params.provider.Arguments> = listOf(
            args("core/audio", setOf("core.domain")),
            args("core/data", setOf("core.domain")),
            args("core/ui", setOf("core.designsystem")),
            args(
                "app",
                setOf(
                    "core.designsystem",
                    "feature.measure",
                    "feature.history",
                    "feature.settings",
                    "feature.about",
                ),
            ),
        )

        private fun args(
            modulePath: String,
            deps: Set<String>,
        ): org.junit.jupiter.params.provider.Arguments =
            org.junit.jupiter.params.provider.Arguments.of(modulePath, deps)
    }
}
