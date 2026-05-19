package ru.dmdp.tishina.buildlogic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

class ProjectStructureTest {

    private val repoRoot: File = File("../..").canonicalFile

    @Test
    @DisplayName("core:domain stays pure Kotlin (no com.android.* plugins, no androidx.* deps)")
    fun coreDomainIsPureKotlin() {
        val buildFile = File(repoRoot, "core/domain/build.gradle.kts")
        assertTrue(buildFile.isFile, "missing $buildFile")
        val text = buildFile.readText()

        assertFalse(
            text.contains(Regex("""id\(["']com\.android\.""")),
            "core:domain must not apply any com.android.* Gradle plugin",
        )
        assertFalse(
            text.contains(Regex("""(libs\.plugins\.tishina\.android\.|libs\.plugins\.android\.)""")),
            "core:domain must not apply Android convention/library plugins",
        )
        assertFalse(
            text.contains(Regex("""androidx[.\-]""")),
            "core:domain must not depend on androidx.* libraries (pure Kotlin/JVM module)",
        )
    }

    @Test
    @DisplayName("every declared module has a build.gradle.kts on disk")
    fun everyModuleHasBuildFile() {
        val modules = listOf(
            "app",
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
        modules.forEach { path ->
            val buildFile = File(repoRoot, "$path/build.gradle.kts")
            assertTrue(buildFile.isFile, "missing build.gradle.kts for module :$path")
        }
    }

    @Test
    @DisplayName("settings.gradle.kts declares all 11 modules and includeBuild(build-logic)")
    fun settingsDeclaresAllModulesAndIncludesBuildLogic() {
        val settings = File(repoRoot, "settings.gradle.kts").readText()
        assertTrue(
            settings.contains("includeBuild(\"build-logic\")"),
            "settings.gradle.kts must includeBuild(\"build-logic\")",
        )
        val expected = listOf(
            ":app",
            ":core:designsystem",
            ":core:ui",
            ":core:domain",
            ":core:data",
            ":core:audio",
            ":core:testing",
            ":feature:measure",
            ":feature:history",
            ":feature:settings",
            ":feature:about",
        )
        expected.forEach { module ->
            assertTrue(
                settings.contains("include(\"$module\")"),
                "settings.gradle.kts must declare include(\"$module\")",
            )
        }
    }
}
