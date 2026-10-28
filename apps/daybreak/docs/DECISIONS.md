# Architecture decision records

This is Daybreak's living decision log. Record significant architecture, product-behavior, dependency, and reliability decisions here when they are made. Decisions explain the choice and its tradeoffs; implementation status is tracked separately so an accepted direction is not mistaken for finished code.

The initial ADR-01 through ADR-15 entries below describe prototype choices. New decisions use the detailed format below, with a stable ID, date, status, context, choice, alternatives, consequences, and implementation status. If a decision changes, add a superseding record and link both entries rather than erasing the original reasoning. Routine refactoring that introduces no new design choice does not require an ADR.

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
| [ADR-16](#adr-16--mvvm-with-unidirectional-data-flow) | MVVM with unidirectional data flow | Clear UI/state/business boundaries using standard Android components | Full MVI framework deferred; explicit state ownership still requires discipline |

## ADR-16 — MVVM with unidirectional data flow

- **Date:** 2026-10-28
- **Status:** Accepted
- **Implementation:** Partially present in the prototype; refinements below are planned and have not yet been implemented.

### Context

Daybreak needs list, creation, and ringing screens while keeping challenge rules independent of UI and Android delivery mechanics. The architecture must be modular, testable, extensible, and straightforward to explain in interviews. The prototype already has Compose, an `AlarmViewModel`, repository/scheduler interfaces, and a focused completion use case, but exposes mutable screen state and combines several screen responsibilities.

### Decision

Use **MVVM with unidirectional data flow within the existing layered architecture**:

- Compose renders immutable UI state and sends user actions through ViewModel methods. Composables do not mutate ViewModel state directly or own business rules.
- Screen ViewModels expose read-only `StateFlow` state and coordinate actions. Keep mutable flows private and collect UI state with lifecycle awareness.
- Focused use cases enforce meaningful business operations such as scheduling and challenge completion. Introduce them for real rules and test seams, rather than wrapping every repository call in boilerplate.
- Repository and scheduler interfaces keep those rules independent of Room and Android APIs. Adapters implement storage and platform behavior.
- The ringing service owns playback independently of ViewModel and activity lifetimes. ViewModels must not become background alarm-delivery engines.
- Separate list, creation, and ringing ViewModel responsibilities as dedicated screens are extracted. Each screen owns its state contract; avoid one application-wide UI-state object.

### Alternatives considered

| Alternative | Assessment |
| --- | --- |
| Full MVI framework with reducer/effect machinery | Explicit transitions are useful, but a framework adds concepts and ceremony beyond the current needs. Revisit if complex interacting transitions become difficult to test or reason about. |
| Continue growing one ViewModel with publicly mutable flows | Less initial refactoring, but weak state ownership and mixed screen responsibilities make behavior harder to test and explain. |
| Move alarm rules into composables | Easy to start, but couples business behavior to composition and screen lifecycle; unsuitable for reliable background alarms. |

### Consequences and rationale

MVVM, unidirectional data flow, and layers describe complementary boundaries. Together they give us standard Android tooling, explicit state ownership, and independently testable rules without requiring a new state-management framework. The cost is defining screen-state contracts and maintaining clear ownership. This architectural choice alone does not solve process death, scheduling consistency, or audio recovery; those need separate decisions and implementation.

### Follow-up and verification

1. Introduce immutable screen-state models with private mutable flows and read-only public exposure.
2. Extract focused scheduling operations from the ViewModel; retain the common completion gate for both snooze and dismiss.
3. Separate screen ViewModels as the UI is split into list, creation, and ringing screens.
4. Add ViewModel behavior tests and repository integration tests covering validation, loading/errors, repeated actions, and observable persistence.
5. Verify playback remains independent of screen recreation and backgrounding through Android/device checks.

Update this record's implementation status as those changes land. Keep [ARCHITECTURE.md](ARCHITECTURE.md) aligned with implemented behavior and [ROADMAP.md](ROADMAP.md) aligned with remaining work.

## Repository organization

Daybreak is an independent Gradle project under `apps/daybreak/` in a repository of Android samples. Its `core` modules contain Daybreak-specific contracts and persistence, so they stay inside the sample. Each future sample owns its Gradle wrapper, dependency versions, tests, and SDLC documents. This allows samples to demonstrate different architectures and upgrade independently. Extract repository-wide libraries only when multiple applications have demonstrated a stable shared need.

## Important unresolved decisions

Before a production release: durable scheduling reconciliation, persisted ringing sessions and overlapping-alarm queue, audio failure recovery, emergency recovery for inaccessible challenges, recurrence semantics, maximum ringing/snooze policy, and full-screen/foreground-service store eligibility. These are recorded as gaps, not hidden behind abstraction.

## Why not more abstractions?

We do not add a generic `BaseUseCase`, a result wrapper for every return value, a network layer without a backend, a navigation framework for one screen plus a dialog, or an event bus. A single focused completion use case captures a real invariant. Ordinary Kotlin data classes, interfaces, and explicit calls make the rest traceable.
