package dev.notesmaker.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** The `:app` module: target SDK and the ABIs the native engine is built for (ADR-07, ADR-19). */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig {
                targetSdk = TARGET_SDK
                // arm64-v8a: phones (tiers M and H); x86_64: emulator (tier E). ADR-19.
                ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
            }
        }
    }
}
