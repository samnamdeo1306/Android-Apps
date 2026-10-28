# Android sample applications

A collection of independent Android sample applications for learning, experimentation, and interview demonstrations. Each sample owns its architecture, dependencies, build, tests, and design documentation.

## Applications

| Sample | Purpose | Stack | Documentation |
| --- | --- | --- | --- |
| [Daybreak](apps/daybreak/) | Alarm app with question/puzzle challenges for snooze and dismiss | Kotlin, Compose, Room, coroutines, Flow | [Project overview](apps/daybreak/README.md) · [PRD](apps/daybreak/docs/PRODUCT_REQUIREMENTS.md) · [Architecture](apps/daybreak/docs/ARCHITECTURE.md) |

## Repository layout

```text
Android-Apps/
├── apps/
│   └── daybreak/                # Independent Android Studio / Gradle project
│       ├── app/                 # Compose UI and Android integration
│       ├── core/
│       │   ├── domain/          # Daybreak business rules and JVM tests
│       │   └── data/            # Daybreak Room persistence
│       ├── docs/               # PRD, architecture, decisions, testing, roadmap
│       ├── gradle/             # Sample-owned wrapper and version catalog
│       ├── gradlew
│       ├── settings.gradle.kts
│       ├── build.gradle.kts
│       ├── README.md
│       └── CHANGELOG.md
├── docs/                       # Repository-wide conventions
├── .github/workflows/          # Sample-specific CI workflows
└── README.md                   # Sample catalog and entry point
```

Open **`apps/daybreak/`** in Android Studio to work on the alarm app. The repository root is a catalog, with each sample building independently.

## Build Daybreak

With JDK 21 and Android SDK 36 installed:

```sh
cd apps/daybreak
./gradlew :core:domain:test :app:assembleDebug :app:lintDebug
```

In the prepared cloud environment, first run `source /workspace/toolchains/activate.sh`. The APK is generated at `apps/daybreak/app/build/outputs/apk/debug/app-debug.apk` relative to the repository root. See [Daybreak's README](apps/daybreak/README.md) for permissions, device testing, and limitations.

## Add another sample

Create an independent project at `apps/<sample-name>/`, add its documentation and CI workflow, and list it above. Follow the [repository conventions](docs/REPOSITORY_STRUCTURE.md). Keep sample-specific domain and data modules inside their sample; introduce shared libraries only when multiple apps actually need the same stable functionality.
