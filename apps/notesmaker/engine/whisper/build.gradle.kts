plugins {
    alias(libs.plugins.notesmaker.android.library)
}

// whisper.cpp built from a pinned, checksummed release archive (ADR-24, ADR-32).
android {
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        // arm64-v8a: phones (tiers M and H); x86_64: emulator (tier E). ADR-19.
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DNOTESMAKER_WHISPER_VERSION=${libs.versions.whisperCpp.get()}",
                    "-DNOTESMAKER_WHISPER_SHA256=${libs.versions.whisperCppSha256.get()}",
                    "-DANDROID_STL=c++_static",
                )
            }
        }
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "WHISPER_VERSION", "\"${libs.versions.whisperCpp.get()}\"")
    }

    buildFeatures { buildConfig = true }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = libs.versions.cmake.get()
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    testImplementation(testFixtures(project(":core:domain")))
}
