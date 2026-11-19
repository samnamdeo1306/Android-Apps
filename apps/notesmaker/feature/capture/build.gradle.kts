plugins {
    alias(libs.plugins.notesmaker.android.feature)
}

// Capture screen: record or import (FR-01, FR-10). The convention plugin adds the only allowed
// project dependencies (:core:domain, :core:ui, :core:designsystem).
