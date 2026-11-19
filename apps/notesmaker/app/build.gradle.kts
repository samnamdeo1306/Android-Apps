plugins {
    alias(libs.plugins.notesmaker.android.application)
    alias(libs.plugins.notesmaker.android.compose)
    alias(libs.plugins.notesmaker.android.hilt)
}

android {
    namespace = "dev.notesmaker.app"

    defaultConfig {
        applicationId = "dev.notesmaker.app"
        versionCode = 1
        versionName = "0.1.0-dev"
        // Hilt needs its own Application in instrumented tests.
        testInstrumentationRunner = "dev.notesmaker.app.HiltTestRunner"
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

// The composition root: the only module that sees every other module (HLD Section 4.2 rule 5).
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    implementation(project(":core:processing"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":feature:capture"))
    implementation(project(":feature:sessions"))
    implementation(project(":feature:session"))
    implementation(project(":feature:settings"))
    implementation(project(":engine:whisper"))
    implementation(project(":engine:notes-basic"))
    implementation(project(":engine:notes-gemini-nano"))

    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.compose)

    testImplementation(testFixtures(project(":core:domain")))
    androidTestImplementation(testFixtures(project(":core:domain")))
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
