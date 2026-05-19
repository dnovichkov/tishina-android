import com.android.build.gradle.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import ru.dmdp.tishina.buildlogic.libs

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("tishina.android.library")
                apply("tishina.android.compose")
                apply("tishina.android.hilt")
                apply("tishina.jvm.testing")
            }

            extensions.configure<LibraryExtension> {
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:domain"))
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-ktx").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())

                // Spec §7 / plan line 345: every feature gets the shared test stack via :core:testing.
                // Without this, the first Robolectric/Roborazzi/Compose UI test in a feature would
                // either duplicate the toolchain wiring or fail to compile.
                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
