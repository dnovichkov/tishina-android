import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register
import ru.dmdp.tishina.buildlogic.licenses.GenerateOssLicensesTask

/**
 * Phase 6 Task 8 — registers the `generateOssLicenses` task on `:app`.
 *
 * The convention plugin keeps Gradle wiring out of `app/build.gradle.kts` and centralises
 * the configuration choice (`releaseRuntimeClasspath`, `src/main/assets/oss_licenses.json`)
 * so consumers can apply the plugin and forget the details.
 *
 * Ordering note: the task writes into `src/main/assets/`, which AGP's
 * `merge{Variant}Assets` reads as an input. When developers run
 * `./gradlew :app:generateOssLicenses :app:assembleDebug` in one invocation Gradle
 * would otherwise complain about an implicit dependency between the two — we declare
 * `mustRunAfter` so the generation happens first whenever both are requested.
 */
class OssLicensesConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            val generateTask = tasks.register<GenerateOssLicensesTask>("generateOssLicenses") {
                // `releaseRuntimeClasspath` mirrors what ends up inside the published
                // AAB — the dependency set the user actually runs against. Using the
                // debug variant would inflate the list with junit/turbine/mockk POMs.
                configurationName.set("releaseRuntimeClasspath")
                outputJson.set(layout.projectDirectory.file("src/main/assets/oss_licenses.json"))
            }

            // AGP's `merge{Variant}Assets` tasks read everything under src/main/assets,
            // including the file this task writes. Declaring `mustRunAfter` here surfaces
            // the ordering to Gradle so a developer can run "generateOssLicenses +
            // assembleDebug" in a single invocation without tripping the implicit-dep
            // validation. We never want `assemble*` to *require* a fresh generation
            // (drift is enforced separately in CI), hence mustRunAfter and not dependsOn.
            tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
                mustRunAfter(generateTask)
            }
        }
    }
}
