package dev.notesmaker.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Every Android library module: AGP library + Kotlin Android with the shared SDK, Java and test setup. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            // Namespace follows the module path: :core:data -> dev.notesmaker.core.data
            namespace = "dev.notesmaker" + path.replace(':', '.').replace('-', '.')
        }
    }
}
