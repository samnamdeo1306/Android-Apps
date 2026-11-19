package dev.notesmaker.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * A screen-group module. Adds the only project dependencies a feature may have (HLD Section 4.2,
 * rules 2 and 3), so a feature module's build file never declares project dependencies itself.
 * `checkModuleGraph` catches any that are added by hand.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("notesmaker.android.library")
        pluginManager.apply("notesmaker.android.compose")
        pluginManager.apply("notesmaker.android.hilt")

        dependencies {
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:designsystem"))
            add("implementation", libs.library("lifecycle-runtime-compose"))
            add("implementation", libs.library("lifecycle-viewmodel-compose"))
            add("testImplementation", testFixtures(project(":core:domain")))
        }
    }
}
