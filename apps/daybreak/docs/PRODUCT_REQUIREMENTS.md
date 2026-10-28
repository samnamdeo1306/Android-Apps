# Product requirements — Daybreak

Status: first implementation increment, 0.1.0. Owner: project maintainer. Purpose: interview portfolio and practical Android architecture study.

## Problem and audience

Sleepy users dismiss conventional alarms with little thought. Daybreak asks for a small intentional action. The app is for people who want that friction; it does not promise to prevent oversleeping, enforce wakefulness, or defeat operating-system controls.

## Goals

1. Both snooze and dismiss require successful challenge validation.
2. Offer two initial strategies: a personal question/answer chosen during setup and a simple addition puzzle.
3. Support more strategies without changing scheduling, ringing, or the completion flow.
4. Keep data local and architecture small enough to defend in a design review.
5. Clearly explain Android permissions and reliability boundaries.

## First-increment scope

| ID | Requirement | Acceptance criterion |
| --- | --- | --- |
| P1 | Create a named one-time alarm | A confirmed dial/keyboard selection schedules its next local occurrence; a past time means tomorrow. Display and picker follow system 12/24-hour preference; cancel preserves the draft. |
| P2 | Configure personal question | Empty question/answer rejected; correct answer ignores case and outer whitespace |
| P3 | Generate number puzzle | Fresh two-number addition prompt each ringing session; only its numeric answer succeeds |
| P4 | Gate both actions | Wrong answer causes no scheduling/persistence side effects and sound continues |
| P5 | Snooze | Correct answer schedules same alarm ID five minutes from completion |
| P6 | Dismiss | Correct answer marks alarm completed and ends playback |
| P7 | Persist and observe | Room records survive app recreation; Flow updates the list |
| P8 | Explain access | Missing exact-alarm permission prevents scheduling; notification/full-screen access has settings actions |
| P9 | Restore | Future enabled alarms are rescheduled after reboot/update; missed alarms disabled |
| P10 | Delete | A non-ringing alarm can be removed and pending delivery canceled |
| P11 | Edit | Prefill existing configuration, retain ID, cancel without writes. Save & schedule enables the alarm; unchanged future enabled time preserves its instant. Blank answer retains the digest only for the unchanged personal question. Reject stale/missing/ringing edits and report failed restoration. |

Scheduling is absolute after creation: traveling across time zones does not rebase a saved alarm to the same wall-clock hour. The list displays the instant in the current zone. During fall-back DST, Java time uses the earlier offset; during a spring gap, it shifts forward to a valid time. Recurring wall-clock alarms need a separate recurrence policy.

## Interaction

Home → New alarm → name/time/challenge → schedule → ringing challenge → submit answer with dismiss or snooze intent → validate → apply action → stop sound. Incorrect input remains on the challenge screen with feedback. Leaving the activity does not dismiss the alarm. A new puzzle is generated on a new ringing session; rotation keeps the ViewModel session, process recreation can regenerate it.

## Nonfunctional requirements

- Android 8/API 26 minimum; compile/target API 36.
- Coroutine-based asynchronous persistence; lifecycle-aware state collection.
- Stable versioned challenge IDs; no silent fallback for unknown persisted IDs.
- No network, accounts, ads, analytics, or backend in the app.
- Text labels for controls; system font scaling and scrollable content. TalkBack, large fonts, and contrast require device review before accessibility sign-off.
- No repository secrets or release signing keys.

## Not in 0.1.0

Recurring alarms, custom sounds, haptics, maximum snooze policy, photo/QR/motion challenges, multiple simultaneous challenge screens, cloud sync, a widget, localization, migration tests, release signing, or production reliability certification.

The initial overlap policy defers a newly delivered second alarm by one minute while the first is ringing. This is deliberately documented rather than silently replacing the active challenge. A durable queue is a release-hardening requirement.

## Success and release gate

Development success: assemble debug APK, pass real domain tests, run Android lint, and review docs. Product release additionally needs the physical-device acceptance matrix, reliable sound/error recovery, Room integration tests, durable scheduling reconciliation, accessibility review, and Play policy review. No analytics are added merely to measure a portfolio app; record manual test outcomes instead.

## Planned scope after 0.1.0

Resource-based string localization (ADR-18) now has a default English foundation. The proper time picker (ADR-19) is implemented. The shared edit flow (ADR-20) is implemented with best-effort compensation. The remaining requested features are weekday recurrence, verified full-screen/idle delivery, and settings for alarm sounds. See ROADMAP.md for sequencing, acceptance criteria, and the proposed recurrence policy, and ADR-17 for rationale and tradeoffs. These features remain planned; the scope and acceptance table above describe the currently implemented baseline.

## Planned increment sequence

The accepted product direction remains a small, local-first interview sample with a shared challenge gate and extensible strategies. ADR-16 screen-specific MVVM and the ADR-18 default English resource foundation are implemented. The Material time picker is implemented (ADR-19). Editing is implemented (ADR-20). Next implement idle/full-screen delivery hardening, sound settings, and recurrence in ROADMAP.md. New challenge types stay behind the registry and shared completion gate; feature work must retain both action-gate tests and updated design/validation records.
