plugins {
    `kotlin-dsl`
}

group = "dev.notesmaker.buildlogic"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // compileOnly: the root build puts the real plugins on the class path (see ../../build.gradle.kts).
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnit()
}

gradlePlugin {
    plugins {
        register("jvmLibrary") {
            id = "notesmaker.jvm.library"
            implementationClass = "dev.notesmaker.buildlogic.JvmLibraryConventionPlugin"
        }
        register("androidLibrary") {
            id = "notesmaker.android.library"
            implementationClass = "dev.notesmaker.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "notesmaker.android.compose"
            implementationClass = "dev.notesmaker.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "notesmaker.android.feature"
            implementationClass = "dev.notesmaker.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "notesmaker.android.hilt"
            implementationClass = "dev.notesmaker.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "notesmaker.android.room"
            implementationClass = "dev.notesmaker.buildlogic.AndroidRoomConventionPlugin"
        }
        register("androidApplication") {
            id = "notesmaker.android.application"
            implementationClass = "dev.notesmaker.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("moduleGraph") {
            id = "notesmaker.module.graph"
            implementationClass = "dev.notesmaker.buildlogic.ModuleGraphConventionPlugin"
        }
    }
}
