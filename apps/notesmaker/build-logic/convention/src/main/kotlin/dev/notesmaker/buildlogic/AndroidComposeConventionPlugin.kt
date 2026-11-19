package dev.notesmaker.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Compose compiler, BOM, tooling and Compose UI test setup. Apply after an Android plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        check(pluginManager.hasPlugin("com.android.base")) {
            "Apply notesmaker.android.library or notesmaker.android.application before notesmaker.android.compose in $path"
        }
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        // Look up by raw class: the reified getByType<CommonExtension<*, ...>>() matches the
        // parameterized type exactly and misses ApplicationExtension and LibraryExtension.
        extensions.getByType(CommonExtension::class.java).buildFeatures.compose = true

        dependencies {
            val bom = platform(libs.library("compose-bom"))
            add("implementation", bom)
            add("androidTestImplementation", bom)
            add("implementation", libs.library("compose-ui"))
            add("implementation", libs.library("compose-material3"))
            add("implementation", libs.library("compose-ui-tooling-preview"))
            add("debugImplementation", libs.library("compose-ui-tooling"))
            // Hosts composables in an empty activity for createComposeRule() in library modules.
            add("debugImplementation", libs.library("compose-ui-test-manifest"))
            add("androidTestImplementation", libs.library("compose-ui-test-junit4"))
        }
    }
}
