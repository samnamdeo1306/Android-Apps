package dev.notesmaker.buildlogic

import com.android.build.api.dsl.LibraryExtension
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room with KSP. Schemas are exported to `schemas/` and committed (LLD Section 3.2), and the same
 * directory is an androidTest asset so MigrationTestHelper can open every released version.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        val schemaDir = layout.projectDirectory.dir("schemas")
        extensions.configure<KspExtension> {
            arg("room.schemaLocation", schemaDir.asFile.absolutePath)
            arg("room.generateKotlin", "true")
        }
        extensions.configure<LibraryExtension> {
            sourceSets.getByName("androidTest").assets.srcDir(schemaDir)
        }

        dependencies {
            add("implementation", libs.library("room-runtime"))
            add("implementation", libs.library("room-ktx"))
            add("ksp", libs.library("room-compiler"))
            add("androidTestImplementation", libs.library("room-testing"))
        }
    }
}
