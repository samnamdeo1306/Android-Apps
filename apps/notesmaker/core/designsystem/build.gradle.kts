plugins {
    alias(libs.plugins.notesmaker.android.library)
    alias(libs.plugins.notesmaker.android.compose)
}

// Theme and shared composables. Depends on Compose only (HLD Section 4.1).
dependencies {
    api(libs.compose.material3)
    implementation(libs.compose.material.icons)
}
