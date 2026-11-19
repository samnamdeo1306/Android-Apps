# NotesMaker

Part of the [Android sample applications repository](../../README.md).

NotesMaker helps teachers and instructors turn a class into study notes. Record a live session or import an audio file, and the app transcribes it on the device and produces a title, summary, sections and highlights, with the original audio kept as a voice note. Processed sessions are listed on a separate screen, sortable by title or date.

## Status

Milestone 1 of the low-level design build order is in place: the Gradle multi-module skeleton, the whisper.cpp native build, the Room database, Hilt wiring, CI, and the test infrastructure every later milestone builds on. The app opens to an empty Capture screen; recording, import and processing come next.

| Document | Status |
| --- | --- |
| [Product requirements](docs/PRODUCT_REQUIREMENTS.md) | Draft v0.2 |
| [Decision log](docs/DECISIONS.md) | ADR-01 to ADR-35 (ADR-19 accepted, others proposed) |
| [High-level design](docs/HIGH_LEVEL_DESIGN.md) | Draft v0.3 |
| [Low-level design](docs/LOW_LEVEL_DESIGN.md) | Draft v0.1 |
| [Internal API design](docs/API_DESIGN.md) | Draft v0.1 |
| [whisper.cpp module](engine/whisper/README.md) | Build, tests and upgrade procedure |

Stack: Kotlin 2.2, Jetpack Compose with Material 3, whisper.cpp 1.9.5 through the NDK, Room, Hilt, coroutines and Flow. DataStore, WorkManager, Navigation 3 and Media3 arrive with the milestones that need them.

## Modules

| Module | Kind | Holds | Tests |
| --- | --- | --- | --- |
| `:app` | Application | Hilt application, activity, engine set bindings | Instrumented: Hilt graph, launch |
| `:core:domain` | JVM | Model, ports, plug-in APIs, `Outcome` and failures; fakes and contract suites in `testFixtures` | JVM: models, codecs, registry, fakes against the contracts |
| `:core:data` | Android | Room schema v1, DAOs, repositories, DI modules | JVM: mappers; instrumented: repository contracts, schema, cascades, indexes |
| `:core:designsystem` | Android, Compose | Theme and shared components | Instrumented: Compose UI |
| `:core:ui` | Android | `UiText` and failure messages | JVM |
| `:feature:capture` | Android, Compose | Capture screen | Instrumented: Compose UI |
| `:engine:whisper` | Android, NDK | whisper.cpp build and JNI bridge | JVM: segment mapping; instrumented: native load |
| `:core:media`, `:core:processing`, `:feature:sessions`, `:feature:session`, `:feature:settings`, `:engine:notes-basic`, `:engine:notes-gemini-nano` | | Empty until their milestone | |

Module dependency rules from the HLD are enforced by `./gradlew checkModuleGraph`. Shared build configuration is in the `build-logic` convention plugins.

## Testing

Every port has an in-memory fake in `core/domain/src/testFixtures`, and every repository and plug-in API has a contract suite there (ADR-31). The fakes run the suites on the JVM; the Room repositories run the same suites on a device, so a test written against a fake tells the truth about production. Tests use JUnit 4, kotlin-test, coroutines-test and Turbine, with no mocking library (ADR-35).

## Build and test

With JDK 21 and Android SDK 36, NDK 28.2.13676358 and CMake 3.22.1 installed, from this directory:

```sh
./gradlew checkModuleGraph :build-logic:convention:test   # module rules
./gradlew :core:domain:test testDebugUnitTest            # JVM tests
./gradlew assembleDebug lintDebug                        # APK and lint
./gradlew connectedDebugAndroidTest                      # device or emulator tests
```

CI runs all of these in `.github/workflows/notesmaker.yml`, with the device tests on API 26 and API 35 emulators (ADR-34).
