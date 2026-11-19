plugins {
    alias(libs.plugins.notesmaker.jvm.library)
}

// The built-in extractive note generator (ADR-04, LLD Section 7.4). Pure Kotlin. Lands with LLD Section 12 step 5.
dependencies {
    implementation(project(":core:domain"))
    testImplementation(testFixtures(project(":core:domain")))
}
