plugins {
    alias(libs.plugins.notesmaker.android.library)
    alias(libs.plugins.notesmaker.android.room)
    alias(libs.plugins.notesmaker.android.hilt)
}

// Room, repositories and environment adapters (HLD Section 4.1). Depends only on the domain.
dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.android)

    testImplementation(testFixtures(project(":core:domain")))
    // The repository contract suites run against Room on a device (ADR-31).
    androidTestImplementation(testFixtures(project(":core:domain")))
}
