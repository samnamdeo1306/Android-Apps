package dev.notesmaker.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/**
 * Registers `checkModuleGraph` on the root project. It reads every project dependency declared in
 * any configuration (main, test and androidTest alike) and fails the build if one breaks the
 * dependency rules of HLD Section 4.2. The rules themselves live in [ModuleGraphRules].
 */
class ModuleGraphConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        check(target == target.rootProject) { "notesmaker.module.graph is applied to the root project only" }
        target.tasks.register<CheckModuleGraphTask>("checkModuleGraph") {
            group = "verification"
            description = "Fails if a project dependency breaks the module rules (HLD Section 4.2)."
            val root = project
            edges.set(
                root.provider {
                    root.subprojects.flatMap { module ->
                        module.configurations.flatMap { configuration ->
                            configuration.dependencies.withType(ProjectDependency::class.java)
                                .map { "${module.path} -> ${it.path} (${configuration.name})" }
                        }
                    }.distinct().sorted()
                },
            )
        }
    }
}

abstract class CheckModuleGraphTask : DefaultTask() {
    /** One entry per declared edge: `:from -> :to (configuration)`. */
    @get:Input
    abstract val edges: ListProperty<String>

    @TaskAction
    fun check() {
        val parsed = edges.get().map { line ->
            val (from, rest) = line.split(" -> ", limit = 2)
            val to = rest.substringBefore(" (")
            ModuleGraphRules.Edge(from, to, rest.substringAfter(" (").removeSuffix(")"))
        }
        val violations = ModuleGraphRules.violations(parsed)
        if (violations.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                "Module graph rules broken (HLD Section 4.2):\n" + violations.joinToString("\n") { "  - $it" },
            )
        }
        logger.lifecycle("Module graph OK: ${parsed.size} project dependencies checked.")
    }
}
