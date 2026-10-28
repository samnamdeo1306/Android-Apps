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

## 2026-10-28 — MVVM refactor, first increment

- Extracted CreateAlarm and DeleteAlarm domain use cases; creation has an injectable Clock and preserves scheduling-denial compensation.
- Exposed ViewModel flows read-only; snackbar acknowledgement now sends a command. Guard commands before coroutine launch.
- Recorded the accepted MVVM/UDF direction and remaining screen-specific ViewModel work in ADR-16.
- Local validation using JBR 21: 14 domain tests passed, zero failures/errors/skips; debug APK assembly and lint succeeded. Device behavior was not retested in this increment.

## 2026-10-28 — Screen-specific MVVM increment

- Replaced the application-aware AlarmViewModel with constructor-injected list, creation, and ringing ViewModels, each exposing cohesive read-only UI state.
- Creation success now flows through acknowledged state; drafts and private challenge sessions survive rotation in memory. Personal creation answers clear on close/save.
- Added an AlarmPlayback adapter; the foreground service still owns audio and both actions retain the shared completion gate.
- Kept active-but-unavailable challenges in ringing mode, preventing ordinary list/create controls from appearing while sound continues.
- Added JVM ViewModel behavior tests and updated architecture, ADR-16, product sequencing, test plan, and interview guide. Localization (ADR-18) remains the next increment; no Room schema or toolchain version change.

Validation for the screen-specific MVVM increment: `:core:domain:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` succeeded with JBR 21. Reports contain 14 domain tests and 8 ViewModel tests, zero failures/errors/skips. Domain tasks reused up-to-date results; the new ViewModel suite ran in this build. Debug APK assembly and lint passed. Physical-device rotation, playback, idle delivery, and lock-screen presentation were not rerun in this increment.

## 2026-10-28 — String localization foundation

- Moved app-owned UI, feedback, challenge, notification/channel and manifest text into default English string/plural resources.
- Replaced raw exception feedback with typed domain failures mapped to Android presentation resources; domain stays independent of Android.
- Generated addition prompts now hold operands; rendering translates wording/numerals without recreating the challenge session. Personal questions and stable IDs remain unchanged.
- Added locale-aware date/time display, pluralized alarm counts, and debug pseudolocales. Time input stays HH:mm until the picker increment.
- Updated ADR-18, architecture/extension guidance, roadmap and localization acceptance checklist. No schema or toolchain upgrade.

Localization validation: 16 domain tests and 10 ViewModel tests passed (zero failures/errors/skips); debug APK assembly and lint succeeded. Lint reports dependency-update warnings only. Static resource-reference review found 66 resource entries and no missing references. Installed the debug build on the running Pixel 3a API 30 emulator and visually checked the English home screen (including system 12-hour display) and creation dialog; closed without scheduling an alarm. Expanded/RTL pseudolocale layouts, 200% fonts, live Android locale-switch behavior while ringing, and notification localization remain manual acceptance checks; human translations are not included.

## 2026-10-28 — Time picker

- Replaced the free-form time text field with a rounded, gradient time card and a Material 3 dial/keyboard dialog.
- Follow the system 12/24-hour preference; confirm applies to the in-memory draft, while cancel/back/outside dismissal discards pending time edits.
- Reusable LocalTime-based presentation component; no scheduling side effects, schema changes or new dependencies. Short/large-font displays start in keyboard mode; dialog content scrolls and actions wrap.
- Reviewed Sinasamaki's ChromaDial dial/customization articles for visual inspiration; retained standard Material controls and documented the alpha dependency/maintenance tradeoff in ADR-19. Updated requirements, architecture and roadmap.

Time-picker validation: `:core:domain:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` passed (16 domain and 10 ViewModel tests, zero failures/errors/skips; domain results reused as up-to-date). Lint has dependency-update warnings only. API 30 emulator checks verified the 12-hour card/dial/input presentation, cancel preserving 7:00 AM after a pending edit, and keyboard confirmation updating the draft to 9:09 PM without scheduling. Remaining device checks are listed in TEST_PLAN.md; no full accessibility sign-off is claimed.

## 2026-10-28 — Edit saved alarms

- Added Edit actions and a shared AlarmEditorViewModel/dialog for new and existing alarms, reusing the time picker and localized validation.
- Retain ID and unchanged personal-answer digest; changed questions require a new answer. Save & schedule enables the alarm; unchanged future enabled times preserve their scheduled instant.
- Reject stale, missing and ringing edits. UpdateAlarm uses persist-then-schedule and best-effort compensation, including cancellation; incomplete restoration has explicit recovery feedback.
- Shared domain preparation avoids duplicate create/edit rules. No schema migration, new dependency or challenge/playback ownership change.
- ADR-20 documents rationale, alternatives, tested compensation and remaining concurrency/crash/reconciliation limits. The custom Sinasamaki-style dial remains low-priority P3 work.

Editing validation: 31 domain tests + 15 ViewModel tests passed with zero failures/errors/skips; debug assembly and lint succeeded. Final lint has dependency-update warnings only. API 30 emulator review verified Edit entry, prefilled name/time/challenge and save/reschedule copy; closed without saving. On-device rescheduling/delivery, permission recovery, rotation and near-trigger concurrency remain in TEST_PLAN.md. Durable reconciliation remains unimplemented, explicitly documented in ADR-20.
