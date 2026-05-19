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
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureDetekt()
            configureSpotless()
            if (path != ":core:testing") {
                configureKover()
            }
        }
    }
}
