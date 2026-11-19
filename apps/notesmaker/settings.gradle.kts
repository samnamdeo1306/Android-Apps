pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        // Google's mirror of Maven Central. CI runners get intermittent "not found" answers from
        // Maven Central and the Plugin Portal's proxy of it; this lets resolution fall through.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Google's mirror of Maven Central. CI runners get intermittent "not found" answers from
        // Maven Central and the Plugin Portal's proxy of it; this lets resolution fall through.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
    }
}

rootProject.name = "NotesMaker"

// Module map and dependency rules: docs/HIGH_LEVEL_DESIGN.md Section 4, enforced by `checkModuleGraph`.
include(":app")
include(":core:domain", ":core:data", ":core:media", ":core:processing", ":core:designsystem", ":core:ui")
include(":feature:capture", ":feature:sessions", ":feature:session", ":feature:settings")
include(":engine:whisper", ":engine:notes-basic", ":engine:notes-gemini-nano")
