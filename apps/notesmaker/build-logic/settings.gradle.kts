pluginManagement {
    // kotlin-dsl pulls the Kotlin Gradle plugin and stdlib from Maven Central.
    repositories {
        mavenCentral()
        // Google's mirror of Maven Central. CI runners get intermittent "not found" answers from
        // Maven Central and the Plugin Portal's proxy of it; this lets resolution fall through.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Google's mirror of Maven Central. CI runners get intermittent "not found" answers from
        // Maven Central and the Plugin Portal's proxy of it; this lets resolution fall through.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
