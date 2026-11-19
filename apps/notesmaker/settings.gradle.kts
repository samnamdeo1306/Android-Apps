pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NotesMaker"

// Module map and dependency rules: docs/HIGH_LEVEL_DESIGN.md Section 4, enforced by `checkModuleGraph`.
include(":app")
include(":core:domain", ":core:data", ":core:media", ":core:processing", ":core:designsystem", ":core:ui")
include(":feature:capture", ":feature:sessions", ":feature:session", ":feature:settings")
include(":engine:whisper", ":engine:notes-basic", ":engine:notes-gemini-nano")
