plugins {
    alias(libs.plugins.notesmaker.android.library)
    alias(libs.plugins.notesmaker.android.compose)
}

// Shared presentation helpers: UiText and the one failure-to-message mapping (API design Section 1.3).
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    testImplementation(testFixtures(project(":core:domain")))
}
