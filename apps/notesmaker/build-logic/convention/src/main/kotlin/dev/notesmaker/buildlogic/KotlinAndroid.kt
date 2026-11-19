package dev.notesmaker.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal const val COMPILE_SDK = 36
internal const val MIN_SDK = 26 // ADR-07
internal const val TARGET_SDK = 36 // ADR-07
internal const val JAVA_VERSION = 21

/** Shared Android + Kotlin setup for every Android module (application and library). */
internal fun Project.configureKotlinAndroid(android: CommonExtension<*, *, *, *, *, *>) {
    android.apply {
        compileSdk = COMPILE_SDK
        defaultConfig {
            minSdk = MIN_SDK
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
        testOptions {
            animationsDisabled = true
            // A JVM unit test that touches an unmocked android.* method should fail loudly,
            // not silently receive 0/null. Such a test belongs in androidTest.
            unitTests.isReturnDefaultValues = false
        }
        lint {
            abortOnError = true
            checkReleaseBuilds = false
            // No baseline: lint starts clean and stays clean (LLD Section 1.1).
            disable += "GradleDependency" // version bumps are deliberate, recorded in the catalog
            disable += "AndroidGradlePluginVersion"
        }
        packaging {
            resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
        }
    }

    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(JAVA_VERSION)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    configureTestLogging()
    addCommonTestDependencies()
}

internal fun Project.addCommonTestDependencies() {
    dependencies {
        add("testImplementation", libs.library("junit"))
        add("testImplementation", libs.library("kotlin-test"))
        add("testImplementation", libs.library("coroutines-test"))
        add("testImplementation", libs.library("turbine"))
        if (pluginManager.hasPlugin("com.android.base")) {
            add("androidTestImplementation", libs.library("androidx-test-core"))
            add("androidTestImplementation", libs.library("androidx-test-runner"))
            add("androidTestImplementation", libs.library("androidx-test-rules"))
            add("androidTestImplementation", libs.library("androidx-test-junit"))
            add("androidTestImplementation", libs.library("kotlin-test"))
            add("androidTestImplementation", libs.library("coroutines-test"))
            add("androidTestImplementation", libs.library("turbine"))
        }
    }
}

internal fun Project.configureTestLogging() {
    tasks.withType<Test>().configureEach {
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}
