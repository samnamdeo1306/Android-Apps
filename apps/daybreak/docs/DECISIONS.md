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
- **Implementation:** Implemented for the current list, creation, and ringing flows. Ordinary constructor-injected AlarmListViewModel, AlarmEditorViewModel (creation and editing; ADR-20), and RingingViewModel replace AlarmViewModel. Each exposes a single read-only StateFlow of immutable state. CreateAlarm/DeleteAlarm own business rules; CompleteAlarmChallenge remains the shared exit gate. Creation success is acknowledged state rather than a UI callback. AlarmPlayback hides Android shutdown mechanics and only requests stopping after successful completion; the service owns the player. Shared active ID is read-only. Activity-scoped instances preserve rotation state; the tradeoff is explicit draft reset/answer clearing on close/save and no process-death draft restoration. Unsupported/loading-failed active challenges keep ringing mode and do not expose ordinary alarm controls. Screen behavior tests cover failure paths and duplicate commands. English string/error mapping is implemented by ADR-18, and durable session/scheduling reconciliation remains P0 work. Room integration tests and physical-device lifecycle checks remain outstanding.

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

The composition root uses explicit ViewModel factories rather than a DI framework; screen-specific ViewModels require deliberate shared active-alarm coordination. These are implementation tradeoffs within the accepted decision.

### Follow-up and verification

1. Introduce immutable screen-state models with private mutable flows and read-only public exposure.
2. Extract focused scheduling operations from the ViewModel; retain the common completion gate for both snooze and dismiss.
3. Separate screen ViewModels as the UI is split into list, creation, and ringing screens.
4. Add ViewModel behavior tests and repository integration tests covering validation, loading/errors, repeated actions, and observable persistence.
5. Verify playback remains independent of screen recreation and backgrounding through Android/device checks.

Items 1–3 and the ViewModel portion of item 4 are implemented. Repository integration tests and device verification in items 4–5 remain planned. Update this record's implementation status as those changes land. Keep [ARCHITECTURE.md](ARCHITECTURE.md) aligned with implemented behavior and [ROADMAP.md](ROADMAP.md) aligned with remaining work.

## Repository organization

Daybreak is an independent Gradle project under `apps/daybreak/` in a repository of Android samples. Its `core` modules contain Daybreak-specific contracts and persistence, so they stay inside the sample. Each future sample owns its Gradle wrapper, dependency versions, tests, and SDLC documents. This allows samples to demonstrate different architectures and upgrade independently. Extract repository-wide libraries only when multiple applications have demonstrated a stable shared need.

## Important unresolved decisions

Before a production release: durable scheduling reconciliation, persisted ringing sessions and overlapping-alarm queue, audio failure recovery, emergency recovery for inaccessible challenges, recurrence semantics, maximum ringing/snooze policy, and full-screen/foreground-service store eligibility. These are recorded as gaps, not hidden behind abstraction.

## Why not more abstractions?

We do not add a generic `BaseUseCase`, a result wrapper for every return value, a network layer without a backend, a navigation framework for one screen plus a dialog, or an event bus. A single focused completion use case captures a real invariant. Ordinary Kotlin data classes, interfaces, and explicit calls make the rest traceable.

## ADR-17 — Next alarm UX increments (2026-10-28)

**Status:** Accepted scope; recurrence policy remains proposed.

**Decision:** Plan a shared create/edit time picker, editing, full-screen/idle delivery hardening, a sound settings screen, and weekday recurrence. Sequence them after the screen-specific MVVM split as detailed in ROADMAP.md. These expand the product scope beyond the 0.1.0 baseline; they do not change today's behavior.

**Rationale:** Time entry, correcting a saved alarm, repeat schedules, visible lock-screen delivery, and choosing sound are core alarm workflows. Shared editor state avoids separate create/edit validation rules. Sound preferences belong behind a settings repository; the service remains the playback owner.

**Alternatives and tradeoffs:** Free-form time alone is cheap but error-prone; retain keyboard entry only as an accessible picker mode. Separate create/edit forms duplicate rules. Global sound is a smaller first step than per-alarm configuration but offers less customization. Persist a URI rather than copying audio files initially; URI access loss requires playback fallback. Recurrence requires rule/occurrence separation and migration rather than adding a repeat flag to an epoch timestamp. Android controls full-screen presentation, so unconditional activity launch is not the design.

**Implementation status:** Planned, no feature implementation in this increment. Full-screen intent, high-importance alarm notification, lock-screen activity flags, and setAlarmClock already exist; device/Doze coverage and failure handling remain outstanding. The recurrence time-zone/DST policy in ROADMAP.md is a proposal requiring explicit acceptance before implementation; ADR-10 and ADR-15 continue to describe the running baseline.

**Platform references:** [Full-screen intent limits](https://source.android.com/docs/core/permissions/fsi-limits), [time-sensitive notifications](https://developer.android.com/develop/ui/compose/notifications/create-notification), and [alarm scheduling and idle delivery](https://developer.android.com/develop/background-work/services/alarms). Full-screen access and device state influence presentation; repeating OS alarms are inexact, so recurrence should calculate successive exact occurrences.

## ADR-18 — Resource-based string localization (2026-10-28)

**Status:** Accepted; default English foundation implemented.

**Decision:** Move all app-owned user-visible text out of Kotlin literals into Android string and plural resources. Begin with a complete default English resource set before expanding the editor and settings. Compose resolves resources when rendering; Android adapters resolve notification and channel text. ViewModels carry message identifiers/data, while domain rules report typed failures and structured challenge prompt data without Android resource IDs. User-entered alarm names and personal questions remain unchanged.

**Rationale:** Central resources enable translation and consistent wording, plural rules, accessibility labels, and locale-sensitive formatting. Keeping resource resolution at the Android boundary preserves JVM-testable domain code and allows the UI to resolve text after locale changes.

**Alternatives and tradeoffs:** Hardcoded English is initially quicker but scatters copy and prevents translation. Passing Context or Android resource IDs into domain would simplify lookup but break its platform independence. Typed failure/prompt mapping adds presentation code; it prevents raw exception messages from becoming the user-facing API. Use placeholders and plurals rather than concatenating translated fragments. Preserve challenge IDs and the current answer-normalization contract so localization does not invalidate existing answers.

**Implementation status:** Default English resource extraction implemented. Screen text (including text-field accessibility labels), snackbar/validation messages, challenge titles, generated puzzle wording, notification/channel copy, and the manifest label use resources. Counts use plurals; date display follows the configuration locale and time display follows the system 12/24-hour preference. The former HH:mm text entry has been replaced by ADR-19; draft serialization remains an internal ISO local-time string. Debug builds enable generated en-XA/ar-XB pseudolocales; no human-translated languages are claimed. Visual RTL/large-font and live locale-change acceptance remain device checks.

**Implementation detail and tradeoff:** ViewModel message state stores app resource IDs, resolved by Compose for the current configuration. A separate generic text wrapper is unnecessary for the current fixed messages; parameterized future messages can add a small typed payload. Domain AlarmFailureReason/AlarmFailure carry no Android IDs or display copy; unknown exceptions map to safe operation-specific resources. The Android scheduler converts exact-access failures to a domain reason. Challenge titles map stable IDs at the presentation boundary; each new strategy needs localized metadata and a renderer where its prompt shape differs. ChallengePrompt carries UserText or Addition operands, so localization never regenerates the session or changes its expected digest. Personal question content is displayed verbatim; localized numeral input is accepted by the addition validator. Channel copy resolves when the service builds its notification; existing OS/user channel customization remains system-controlled. No database migration or persisted challenge-ID change is required.

## ADR-19 — Material time picker with a separate confirmation step

- **Date:** 2026-10-28
- **Status:** Accepted
- **Implementation:** Implemented in AlarmTimePicker.kt; validation results are recorded in CHANGELOG.md and TEST_PLAN.md.
- **Follow-up priority (2026-10-28):** Maintainer explicitly deferred an actual Sinasamaki-style custom dial to an optional, low-priority todo (ROADMAP.md, P3). The Material dial remains the accepted implementation. Custom visual interaction adds review and accessibility work without being necessary for the core alarm flow; revisit after higher-priority functionality and reliability work. No custom-dial implementation is planned for the current increment.

### Context and decision

The alarm editor needs precise time selection, system 12/24-hour formatting, keyboard access, and a calm visual style that can be explained without a custom gesture framework. Replace the free-form HH:mm field with a prominent rounded time card and a Material 3 TimePicker dialog. Offer Material TimeInput through a labeled mode switch. Compact-height/large-font configurations start in keyboard mode; the dialog scrolls and its action row wraps. Retain standard Material touch semantics and Daybreak's navy/peach/mint theme. The time card uses a restrained gradient and content-size animation, informed by Sinasamaki's emphasis on clear dial presentation and responsive interaction.

### Rationale, alternatives and tradeoffs

Reviewed [Sinasamaki's dial introduction](https://www.sinasamaki.com/how-to-create-dials-in-jetpack-compose/) and [custom dial article](https://www.sinasamaki.com/how-to-create-custom-dials/), plus [Android's time-picker dialog guidance](https://developer.android.com/develop/ui/compose/components/time-pickers-dialogs). ChromaDial enables custom tracks/thumbs and gesture animation, but the article labels its API alpha. Adding a custom dial dependency or implementing clock geometry/gestures ourselves would add maintenance, precision, keyboard and accessibility work. Use visual inspiration without copying article code or adding the dependency. A plain text field stays simple but makes formatting the user's responsibility. Standard Material dial/input components offer a smaller implementation with established semantics; actual TalkBack/large-font behavior still needs device review.

### State ownership and behavior

AlarmTimeField accepts LocalTime and emits a confirmed LocalTime, with no ViewModel or scheduler dependency. It is reused by the create/edit screen (ADR-20). The creation route converts between the existing draft's canonical ISO local-time string and LocalTime; display formatting follows configuration locale and the system clock preference. The format is internal, never a user-entry requirement. The picker owns temporary hour/minute/mode state using Compose's saveable picker state; the ViewModel draft changes only on Set time. Cancel, Back, and outside dismissal discard pending edits. Merely selecting a time does not save or schedule an alarm. No Room migration or scheduling/challenge-flow change is needed. This refines ADR-17's picker increment; ADR-18's localized resources and domain boundaries remain in force.

## ADR-20 — Shared alarm editor and best-effort update compensation

- **Date:** 2026-10-28
- **Status:** Accepted
- **Implementation:** Implemented in AlarmEditorViewModel, shared AlarmEditorDialog, and UpdateAlarm. Validation results belong in CHANGELOG.md/TEST_PLAN.md. Durable reconciliation remains P0 work.

### Context and choice

Users need to correct a saved alarm without creating a second alarm or being asked to reveal its existing answer. Rename the creation ViewModel to AlarmEditorViewModel and reuse its immutable state, dialog and ADR-19 picker for both operations. State carries the original snapshot, a draft, visibility, busy/saved flags and localized feedback IDs. Edit prefills label, local time, challenge and question, with an empty answer. Close/cancel clears the draft and writes nothing. Save success closes the editor and clears entered answers; failed saves keep the draft for correction/retry. Validation is visible inside the modal as well as through route feedback.

UpdateAlarm is a focused domain operation using repository/scheduler ports and the current active-ID reader. Shared preparation handles create/edit validation and next-occurrence calculation. The same alarm ID is retained, so the scheduler's existing ID-based PendingIntent replaces its delivery without an explicit cancel-first gap. An unchanged selected time on a future enabled alarm preserves its absolute trigger, including a snoozed occurrence. Otherwise choose the next local occurrence; Save & schedule deliberately enables completed/missed alarms. This action and its consequences are explained in the editor. Production clock/zone is obtained at execution time; tests can still inject a fixed Clock.

Blank personal answer retains the stored digest only when both the challenge and trimmed question are unchanged. A changed question or switch from puzzle to personal question requires a new answer. A supplied answer is normalized/hashed with the existing alarm ID salt. Switching to a puzzle clears question/digest data. Challenge IDs, completion gate, service playback and Room v1 schema stay compatible.

### Alternatives, rationale and tradeoffs

Separate forms/ViewModels duplicate validation and picker behavior. Delete-and-recreate changes ID and loses answer/schedule identity. Scheduling first can deliver before the new persisted configuration exists. Cancel-first introduces a delivery gap. Persist first, then replace the schedule, gives the receiver the new configuration and supports compensation when normal operations throw. On failure/cancellation, attempt to restore the old row, then restore its future enabled schedule (or cancel if the original was disabled/expired). Restoration runs under NonCancellable so screen cancellation does not abandon it. Successful compensation reports the original error; incomplete compensation reports an explicit recovery message rather than success. Users should review and reschedule after restoring exact-alarm access if restoration fails.

Reject missing or changed snapshots before writing, and check the current active ID before loading, before persistence and after persistence. This prevents ordinary ringing edits and stale-editor overwrites, including a tested activation during the write. These are best-effort guards, not a database compare-and-swap or a cross-process transaction. Service/receiver writes can still race the read/write interval. Room and AlarmManager cannot commit atomically; process death bypasses exception compensation, and a rollback failure can leave configuration and OS registration inconsistent. Durable desired-state reconciliation, service coordination and crash-window tests remain release-hardening work in P0. No production-reliability claim follows from the compensation tests.

### Verification and follow-up

Domain tests exercise unchanged-question retention, new-answer salting, strategy switches, time changes, completed rescheduling, missing/stale/active rejection, scheduling failure, rollback failures, activation during save and cancellation after persistence. ViewModel tests exercise prefill/cancel, duplicate save guard, inline failure state and stale/active protection. Device acceptance additionally needs existing alarm delivery identity, rotation during edits, permission revocation/restore and edits close to trigger time. This extends ADR-16's screen-specific state ownership and ADR-17's edit scope; it does not supersede the shared exit gate or the absolute-instant policy for saved one-time alarms.
