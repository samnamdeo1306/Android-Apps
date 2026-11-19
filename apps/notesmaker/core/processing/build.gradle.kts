plugins {
    alias(libs.plugins.notesmaker.android.library)
}

// Processing and model-download workers (HLD Section 4.1). Code lands with LLD Section 12 step 5.
dependencies {
    implementation(project(":core:domain"))
    testImplementation(testFixtures(project(":core:domain")))
}
