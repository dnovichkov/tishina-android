package ru.dmdp.tishina.buildlogic.licenses

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Phase 6 Task 8 — walks the resolved runtime classpath of `:app`, fetches each POM
 * from the Gradle module cache (resolved via a detached `{group}:{artifact}:{version}@pom`
 * configuration), and writes `src/main/assets/oss_licenses.json` consumed by
 * `OssLicensesParser` in `:core:data`.
 *
 * Design notes:
 * - The transformation logic lives in [OssLicensesGenerator] (pure-Kotlin, fully unit
 *   tested in build-logic). This task is intentionally a thin Gradle wrapper.
 * - Configuration cache is intentionally not supported: resolving a detached POM
 *   configuration during execution is the cleanest way to grab the right POM without
 *   pulling licensee plugin (which we explicitly avoided — see plan Task 8). The drift
 *   check in CI re-runs the task on every push, so cache loss is not a performance
 *   concern.
 */
abstract class GenerateOssLicensesTask : DefaultTask() {

    @get:Input
    abstract val configurationName: Property<String>

    @get:OutputFile
    abstract val outputJson: RegularFileProperty

    init {
        group = "build"
        description = "Generate src/main/assets/oss_licenses.json from the resolved runtime classpath POMs."
        notCompatibleWithConfigurationCache(
            "Resolves detached POM configurations during execution to look up Maven license metadata.",
        )
    }

    @TaskAction
    fun run() {
        val configName = configurationName.get()
        val configuration = project.configurations.findByName(configName)
            ?: error("Configuration '$configName' not found on project ${project.path}")

        // ArtifactView with componentFilter is the only resolution path that
        // works cleanly on Android's `releaseRuntimeClasspath`: it skips project
        // dependencies (which would otherwise trip variant-ambiguity errors when
        // we try to pull them as plain JARs) and yields just the external Maven
        // coordinates we actually need POMs for.
        val externalModules = configuration.incoming
            .artifactView {
                componentFilter { id -> id is ModuleComponentIdentifier }
            }
            .artifacts
            .artifacts
            .mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
            .distinctBy { "${it.group}:${it.module}" }

        val resolved = mutableListOf<ResolvedPom>()
        var skipped = 0
        externalModules.forEach { coord ->
            val (group, name, version) = Triple(coord.group, coord.module, coord.version)
            val pomFile = runCatching { resolvePomFile(group, name, version) }
                .getOrElse {
                    logger.warn("Skipping $group:$name:$version — POM unavailable: ${it.message}")
                    skipped++
                    return@forEach
                }

            val pom = runCatching {
                OssLicensesGenerator.parsePom(pomFile.readText(Charsets.UTF_8), group, name, version)
            }.getOrElse {
                logger.warn("Skipping $group:$name:$version — POM unreadable: ${it.message}")
                skipped++
                return@forEach
            }

            resolved += pom
        }

        val json = OssLicensesGenerator.toJson(resolved)
        val outFile = outputJson.get().asFile
        outFile.parentFile?.mkdirs()
        outFile.writeText(json, Charsets.UTF_8)

        val withLicense = resolved.count { !it.licenseName.isNullOrBlank() }
        logger.lifecycle(
            "OssLicenses: scanned=${externalModules.size}, withLicense=$withLicense, skipped=$skipped " +
                "→ ${outFile.relativeTo(project.rootDir).path}",
        )
    }

    private fun resolvePomFile(group: String, name: String, version: String): java.io.File {
        val notation = "$group:$name:$version@pom"
        val dep = project.dependencies.create(notation)
        val config = project.configurations.detachedConfiguration(dep)
        config.isTransitive = false
        return config.resolve().firstOrNull { it.extension == "pom" }
            ?: error("Maven POM not found for $notation")
    }
}
