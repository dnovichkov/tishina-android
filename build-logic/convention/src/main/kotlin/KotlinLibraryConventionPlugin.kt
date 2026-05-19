import org.gradle.api.Plugin
import org.gradle.api.Project
import ru.dmdp.tishina.buildlogic.configureKotlinJvm

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureKotlinJvm()
        }
    }
}
