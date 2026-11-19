package dev.notesmaker.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Pure Kotlin module (`:core:domain`, `:engine:notes-basic`): no Android classes on the class path,
 * explicit API mode, and a `testFixtures` source set for shared fakes and contract suites (ADR-30).
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("java-test-fixtures")

        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(JAVA_VERSION)
            explicitApi()
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }

        tasks.withType<Test>().configureEach { useJUnit() }
        configureTestLogging()
        addCommonTestDependencies()

        dependencies {
            add("implementation", libs.library("coroutines-core"))
            // Contract suites and fakes are JUnit 4 tests themselves, so the fixtures expose the test stack.
            add("testFixturesApi", libs.library("junit"))
            add("testFixturesApi", libs.library("kotlin-test"))
            add("testFixturesApi", libs.library("coroutines-test"))
            add("testFixturesApi", libs.library("turbine"))
        }
    }
}
