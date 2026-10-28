# Daybreak

Part of the [Android sample applications repository](../../README.md). Open this directory directly in Android Studio.

A local-first Android alarm app that asks you to engage your mind before snoozing or dismissing. Built as a small, explainable interview portfolio project, not a guaranteed life-critical alarm.

## What the first increment does

- Schedule multiple one-time alarms for the next occurrence of a local time.
- Choose a personal question with an agreed answer, or a generated addition puzzle.
- Complete the same challenge gate for **both** dismiss and five-minute snooze.
- Store alarms in Room and observe changes through Kotlin Flow.
- Ring through a foreground service with an alarm notification and permitted full-screen intent.
- Restore future alarms after reboot/update; skip alarms missed while powered off.
- Present a calm navy, peach, and mint Material 3 Compose interface.

Name, palette, addition puzzle, five-minute snooze, and one-time scheduling are initial product defaults, not requirements imposed by Android. See the [PRD](docs/PRODUCT_REQUIREMENTS.md).

## Build and run

Use Android Studio with JDK 21, Android SDK platform 36 and build tools 36.0.0. Set your SDK location using Android Studio's ignored `local.properties` or `ANDROID_HOME`.

```sh
./gradlew :core:domain:test :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In the prepared cloud machine, first run `source /workspace/toolchains/activate.sh`. Then work in `/workspace/Android-Apps/apps/daybreak`. Existing task checkouts are already isolated; do not create another worktree for normal tasks.

On a phone, grant exact-alarm access, notifications, and (where available) full-screen access from the home screen. Set the alarm volume audibly. Schedule an alarm a minute or two ahead and test both wrong and correct answers. A successful APK build does not prove lock-screen delivery on a device.

## Architecture at a glance

| Module | Responsibility |
| --- | --- |
| `core:domain` | Plain Kotlin models, repository/scheduler ports, challenge strategies, completion use case, unit tests |
| `core:data` | Room database, DAO, repository adapter, versioned schema |
| `app` | Compose UI, ViewModel, explicit dependency composition, Android alarm/notification/playback adapters |

Dependency direction: `app → data → domain` and `app → domain`. Domain does not depend on Android. We intentionally avoid a module per screen, a generic base ViewModel, and a DI framework until they solve a demonstrated problem.

## Documentation

- [Product requirements and acceptance criteria](docs/PRODUCT_REQUIREMENTS.md)
- [Architecture, data flow, extension guide](docs/ARCHITECTURE.md)
- [Decision records and alternatives](docs/DECISIONS.md)
- [Test strategy and device checklist](docs/TEST_PLAN.md)
- [Privacy, permissions, and failure modes](docs/OPERATIONS.md)
- [Roadmap and suggested improvements](docs/ROADMAP.md)
- [Interview walkthrough](docs/INTERVIEW_GUIDE.md)
- [Release notes](CHANGELOG.md)

Tool versions are pinned in the version catalog. These are a compatible, reproducible modern baseline; “latest available” is not claimed without checking official release notes. Upgrade the Kotlin/Compose compiler/KSP/AGP/Gradle combination together and rerun the validation suite.

The initial cloud build passed all 9 domain tests, APK assembly, and lint. Lint reports newer Kotlin and coroutines releases; these are dependency-update warnings, not proof of compatibility with the pinned AGP/KSP combination. Device checks remain outstanding. CI has been configured but has not yet run remotely.
