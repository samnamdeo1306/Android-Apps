# Architecture decision records

All decisions below are accepted for the prototype, revisitable with evidence. They explain current choices rather than claiming universal best practices.

| ID | Decision | Reason | Alternative / cost |
| --- | --- | --- | --- |
| ADR-01 | Kotlin + Compose + Material 3 | Modern declarative UI with explicit state and accessible base controls | Views remain valid; Compose needs careful recomposition and lifecycle handling |
| ADR-02 | Three Gradle modules | JVM-testable rules and isolated persistence without excessive ceremony | Single module is faster initially; feature-per-module is unnecessary now |
| ADR-03 | Challenge strategy + registry | Add validation behavior without branching throughout ringing/scheduling | A sealed enum alone needs edits for each type; dynamic plugins are unnecessary |
| ADR-04 | One completion use case | Both user actions share the same validation gate | Separate snooze/dismiss handlers risk inconsistent bypass behavior |
| ADR-05 | Room + Flow | Structured local data, schema history, reactive list updates | DataStore fits preferences, not a list of queryable alarm records |
| ADR-06 | AlarmManager.setAlarmClock | User-visible, time-sensitive alarm behavior and system next-alarm integration | WorkManager is deferrable and unsuitable for exact wake-up timing |
| ADR-07 | SCHEDULE_EXACT_ALARM special access | Explicit user-granted access without assuming store eligibility for USE_EXACT_ALARM | Permission may be revoked; creation and snooze must handle failure |
| ADR-08 | Foreground media playback service | Sound survives leaving/recreating the screen | ViewModel playback ties ringing to UI; service still subject to OS restrictions |
| ADR-09 | Explicit dependency composition | Easy to trace in an interview; no annotation-heavy graph | Adopt Hilt when scopes/testing/team size justify it |
| ADR-10 | Fixed five-minute snooze and one-time alarms | Keeps the first end-to-end slice understandable | Recurrence and user preferences need separate product and time-zone policies |
| ADR-11 | Local data, no Internet permission | Works offline with a small privacy surface | No cross-device backup or account recovery |
| ADR-12 | Salted normalized answer digest | Avoid accidental plaintext retention; preserve friendly answer matching | Not a password KDF; dictionary guessing remains possible |
| ADR-13 | Skip powered-off missed alarms | Avoid surprising sound immediately after reboot | Some users prefer catch-up ringing; make it a future explicit policy |
| ADR-14 | Pin a compatible toolchain | Reproducible demos beat unreviewed floating versions | Newer releases require deliberate compatibility checks |
| ADR-15 | Absolute instant for one-time alarms | Simple, predictable storage and scheduling | Wall-clock travel and recurring DST expectations need richer modeling |

## Repository organization

Daybreak is an independent Gradle project under `apps/daybreak/` in a repository of Android samples. Its `core` modules contain Daybreak-specific contracts and persistence, so they stay inside the sample. Each future sample owns its Gradle wrapper, dependency versions, tests, and SDLC documents. This allows samples to demonstrate different architectures and upgrade independently. Extract repository-wide libraries only when multiple applications have demonstrated a stable shared need.

## Important unresolved decisions

Before a production release: durable scheduling reconciliation, persisted ringing sessions and overlapping-alarm queue, audio failure recovery, emergency recovery for inaccessible challenges, recurrence semantics, maximum ringing/snooze policy, and full-screen/foreground-service store eligibility. These are recorded as gaps, not hidden behind abstraction.

## Why not more abstractions?

We do not add a generic `BaseUseCase`, a result wrapper for every return value, a network layer without a backend, a navigation framework for one screen plus a dialog, or an event bus. A single focused completion use case captures a real invariant. Ordinary Kotlin data classes, interfaces, and explicit calls make the rest traceable.
