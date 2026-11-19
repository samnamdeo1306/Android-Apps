plugins {
    alias(libs.plugins.notesmaker.android.library)
}

// Recording service, encoder, decoder, probe and playback (HLD Section 4.1). Code lands with
// LLD Section 12 steps 5 and 6.
dependencies {
    implementation(project(":core:domain"))
    testImplementation(testFixtures(project(":core:domain")))
}
