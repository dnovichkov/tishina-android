package ru.dmdp.tishina.buildlogic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

// Convention plugins are the true source of truth for feature-module dependencies — every
// feature build.gradle.kts only applies `alias(libs.plugins.tishina.android.feature)` and
// relies on AndroidFeatureConventionPlugin to inject :core:designsystem, :core:ui,
// :core:domain. A regex over each feature build script would therefore pass even if the
// convention plugin were to drop those deps. This test guards the convention layer itself
// and is complementary to ModuleDependencyTest, not a replacement.
class ConventionPluginContractTest {

    private val repoRoot: File = File("../..").canonicalFile
    private val conventionSrc: File =
        File(repoRoot, "build-logic/convention/src/main/kotlin")

    @Test
    @DisplayName("AndroidFeatureConventionPlugin injects all core dependencies required by spec §7")
    fun featurePluginInjectsCoreDeps() {
        val source = pluginSource("AndroidFeatureConventionPlugin.kt")
        val required = listOf(
            "project(\":core:designsystem\")",
            "project(\":core:ui\")",
            "project(\":core:domain\")",
        )
        required.forEach { dep ->
            assertTrue(
                source.contains(dep),
                "AndroidFeatureConventionPlugin must add $dep — otherwise feature modules silently lose this dep " +
                    "and ModuleDependencyTest cannot catch it (feature build.gradle.kts files declare no deps themselves)",
            )
        }
    }

    @Test
    @DisplayName("AndroidFeatureConventionPlugin wires testImplementation(:core:testing) — spec §7 / plan line 345")
    fun featurePluginWiresCoreTesting() {
        val source = pluginSource("AndroidFeatureConventionPlugin.kt")
        assertTrue(
            source.contains("testImplementation") && source.contains("project(\":core:testing\")"),
            "AndroidFeatureConventionPlugin must add testImplementation(project(\":core:testing\")). " +
                "Otherwise every feature module that adds a Robolectric/Roborazzi/Compose UI test will " +
                "either duplicate the test-stack wiring or fail to compile.",
        )
    }

    @Test
    @DisplayName("AndroidFeatureConventionPlugin applies Android Library + Compose + Hilt (it composes the feature stack)")
    fun featurePluginAppliesExpectedStack() {
        val source = pluginSource("AndroidFeatureConventionPlugin.kt")
        val required = listOf(
            "tishina.android.library",
            "tishina.android.compose",
            "tishina.android.hilt",
        )
        required.forEach { id ->
            assertTrue(
                source.contains("apply(\"$id\")"),
                "AndroidFeatureConventionPlugin must apply convention plugin '$id'",
            )
        }
    }

    @Test
    @DisplayName("KotlinLibraryConventionPlugin stays pure-Kotlin (no com.android.* / tishina.android.* plugins)")
    fun kotlinLibraryPluginStaysPureKotlin() {
        val source = pluginSource("KotlinLibraryConventionPlugin.kt")
        assertFalse(
            source.contains("apply(\"com.android."),
            "KotlinLibraryConventionPlugin must not apply any com.android.* Gradle plugin (core:domain depends on it staying pure-Kotlin)",
        )
        assertFalse(
            source.contains("apply(\"tishina.android."),
            "KotlinLibraryConventionPlugin must not apply any tishina.android.* convention plugin",
        )
    }

    @Test
    @DisplayName("AndroidLibraryConventionPlugin applies Android Lint + quality stack")
    fun libraryPluginAppliesLintAndQuality() {
        val source = pluginSource("AndroidLibraryConventionPlugin.kt")
        assertTrue(
            source.contains("apply(\"com.android.library\")"),
            "AndroidLibraryConventionPlugin must apply com.android.library",
        )
        assertTrue(
            source.contains("apply(\"tishina.quality\")"),
            "AndroidLibraryConventionPlugin must apply the quality convention (Detekt + Spotless + Kover)",
        )
        assertTrue(
            source.contains("configureAndroidLint"),
            "AndroidLibraryConventionPlugin must wire Android Lint via configureAndroidLint(...)",
        )
    }

    @Test
    @DisplayName("AndroidApplicationConventionPlugin applies Android Lint + quality stack")
    fun applicationPluginAppliesLintAndQuality() {
        val source = pluginSource("AndroidApplicationConventionPlugin.kt")
        assertTrue(
            source.contains("apply(\"com.android.application\")"),
            "AndroidApplicationConventionPlugin must apply com.android.application",
        )
        assertTrue(
            source.contains("apply(\"tishina.quality\")"),
            "AndroidApplicationConventionPlugin must apply the quality convention",
        )
        assertTrue(
            source.contains("configureAndroidLint"),
            "AndroidApplicationConventionPlugin must wire Android Lint via configureAndroidLint(...)",
        )
    }

    private fun pluginSource(fileName: String): String {
        val file = File(conventionSrc, fileName)
        assertTrue(file.isFile, "missing convention plugin source: $file")
        return file.readText()
    }
}
