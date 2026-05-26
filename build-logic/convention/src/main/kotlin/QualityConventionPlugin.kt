import org.gradle.api.Plugin
import org.gradle.api.Project
import ru.dmdp.tishina.buildlogic.configureDetekt
import ru.dmdp.tishina.buildlogic.configureKover
import ru.dmdp.tishina.buildlogic.configureSpotless

/**
 * Applies the project-wide quality stack (Detekt, Spotless+Ktlint, Kover) to a module.
 * Lint is configured separately inside the Android plugins because it is an Android-only tool.
 *
 * Kover is skipped on `:core:testing` because the module ships test utilities — measuring
 * coverage of test-helper code distorts aggregate numbers without surfacing meaningful gaps.
 * `:macrobenchmark` is also skipped — it ships only `androidTest/` sources (no JVM unit
 * tests to instrument), so Kover would produce empty coverage reports while still
 * touching the configuration cache for every Gradle sync.
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureDetekt()
            configureSpotless()
            if (path !in KOVER_SKIP_PROJECTS) {
                configureKover()
            }
        }
    }

    private companion object {
        private val KOVER_SKIP_PROJECTS = setOf(":core:testing", ":macrobenchmark")
    }
}
