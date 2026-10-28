# Release notes

## 0.1.0 — initial development increment (unreleased)

### Repository organization

Daybreak now lives under `apps/daybreak/` as a self-contained Gradle project. Its modules, wrapper, dependency catalog, and documents move together. CI and environment setup use the new project directory.

### Added

- Daybreak Kotlin/Compose Material 3 app shell and alarm creation/list/challenge UI.
- Question and addition challenge strategies with a shared completion gate.
- Three-module architecture, Room persistence/Flow, coroutine commands.
- Exact alarm adapter, foreground alarm audio/notification, conditional full-screen presentation.
- Future-alarm restoration after reboot/update and permission regrant.
- Domain behavior tests and SDLC/design-review documentation.

### Validation

Cloud validation: 9 domain tests passed (0 failures, errors, or skipped tests); debug APK assembly and Android lint completed successfully. The checksum-pinned Gradle wrapper and repeatable installation steps were exercised. Lint also identifies newer Kotlin/Compose compiler and coroutines versions; the compatible baseline is intentionally pinned pending a coordinated toolchain upgrade. Device delivery, sound, lock-screen behavior, and accessibility have not been validated in this cloud machine. The GitHub Actions workflow is added but has not run on GitHub.

### Known limitations

One-time alarms, fixed five-minute snooze, no editing/recurrence, best-effort overlap handling, no durable session/scheduler reconciliation, no robust audio failure recovery, and no release signing/store deployment. This is an interview prototype, not a production alarm guarantee. See the operations guide and roadmap.
