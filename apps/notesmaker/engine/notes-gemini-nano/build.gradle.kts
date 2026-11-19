plugins {
    alias(libs.plugins.notesmaker.android.library)
}

// LanguageModel adapter over the ML Kit GenAI Prompt API (LLD Section 7.5). The beta SDK is added
// here, and only here, when the adapter lands (LLD Section 12 step 8).
dependencies {
    implementation(project(":core:domain"))
    testImplementation(testFixtures(project(":core:domain")))
}
