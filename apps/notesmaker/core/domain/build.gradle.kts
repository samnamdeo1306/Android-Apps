plugins {
    alias(libs.plugins.notesmaker.jvm.library)
}

// Pure Kotlin (HLD Section 4.2 rule 1): no Android, no project dependencies.
dependencies {
    api(libs.coroutines.core)
    api(libs.javax.inject)
}
