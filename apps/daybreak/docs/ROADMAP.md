# Prioritized improvements

## P0 — reliability before real-world release

- ADR-23 implements occurrence phases and a durable desired-schedule outbox with replay tests. Still harden durable challenge sessions, recovery failures and long-running crash/device evidence; Room and AlarmManager are not atomic.
- Introduce a durable overlapping-alarm queue instead of one-minute best-effort deferral.
- Add audio fallback, playback error handling, clear volume/channel diagnostics, and optional vibration. Verify service restart and wakefulness behavior on physical devices.
- Extend the implemented Room migration/outbox and Android delivery tests; complete the device acceptance matrix.
- Design a safe recovery path for unknown/inaccessible challenge types without creating an accidental ordinary dismiss bypass.

## P1 — useful product features

- Recurring weekdays, editing and explicit time-zone/DST behavior are implemented. Add direct enable/disable controls after settings.
- Adjustable snooze interval/count and puzzle difficulty with a preview before saving; avoid frustrating or inaccessible wake-up tasks.
- A second puzzle family (word ordering or pattern matching), with a renderer contract for richer input.
- Accessible light/dark themes, localization, system time-format support, and an actual time picker with text-input fallback.
- Upcoming-alarm summary and a gentle wind-down reminder, opt-in and offline.

## P2 — portfolio depth after the basics

- QR challenge for a chosen room: useful but adds camera/privacy/low-light failure cases and a recovery requirement.
- Optional streaks stored locally; avoid collecting detailed sleep-health data or making medical claims.
- Home-screen widget and quick settings tile for visibility, without bypassing challenge completion.
- Baseline profiles and startup benchmarks after measuring a real performance problem.
- Hilt, feature module extraction, and typed navigation only when app size or team needs make their tradeoffs worthwhile.

## P3 — optional visual polish

- Later todo: explore a Sinasamaki-style custom time dial (potentially ChromaDial) inside AlarmTimeField. Low priority, explicitly deferred by the maintainer on 2026-10-28; not a release blocker. Keep the current Material picker and prioritize editing, delivery reliability, sounds, and recurrence. Revisit only with capacity for precision, 12/24-hour behavior, keyboard fallback, accessibility, and dependency compatibility review. See ADR-19.

## What makes the interview project stronger

Show a complete reliable vertical slice, an honest device test report, a small decision history, and a demonstration of adding one challenge without changing the scheduler. A long list of libraries or unfinished puzzle screens is less defensible than clear invariants and measured reliability.

## Planned usability and alarm features (2026-10-28)

The table records completed foundations and the next planned increment. ADR-16's screen-specific ViewModels are now implemented, giving the editor and settings clear state ownership. The default English localization foundation (ADR-18) is now implemented. The proper time picker (ADR-19) is now implemented. Editing existing alarms is now implemented (ADR-20), with best-effort compensation; ADR-23 adds durable reconciliation. Delivery hardening is implemented (ADR-21), with API 30 emulator coverage and the remaining version/physical-device matrix tracked separately. Recurring alarms and their migration/outbox foundation are implemented (ADR-23). Next: settings and alarm sounds (ADR-22). Translations and visual pseudolocale/RTL acceptance remain follow-up work.

| Order | Increment | Acceptance and dependencies |
| --- | --- | --- |
| Done (foundation) | String localization foundation | Move app-owned user-facing text to Android string/plural resources, including Compose labels, accessibility descriptions, validation feedback, challenge titles/generated prompts, notification text and channel descriptions. Use format arguments instead of concatenation; respect locale and system time format. Map typed domain failures and prompt data to localized text in the Android presentation layer, keeping domain free of Android resources. Preserve user-entered labels/questions verbatim and stable challenge IDs/answer normalization. Verify resource completeness, pseudolocales, RTL, and large fonts. Start with default English resources; additional translated languages are separate increments. |
| Done (implementation) | Proper time picker | Material time picker as the primary input, system 12/24-hour preference, accessible keyboard input fallback; cancel leaves the draft unchanged. Share between create and edit. |
| Done (implementation) | Edit existing alarms | Open a prefilled editor; save preserves alarm ID and replaces pending delivery; cancel changes nothing. Blank personal answer means retain the stored digest for an unchanged question; changing the question requires a new answer. Reject edits while that alarm is ringing. Scheduling failure/compensation tests included; ADR-23 adds durable reconciliation and replay tests; physical crash-window coverage remains P0. |
| Done (implementation; broader device validation pending) | Full-screen and idle delivery hardening | Exercise existing full-screen notification path on locked/screen-off devices and during Doze; test permission denied and channel disabled paths. On unlocked devices accept Android's heads-up presentation. Keep challenge access through the notification and playback in the service. |
| Done (implementation) | Recurring alarms | Select weekdays plus once-only mode; show next occurrence. Persist recurrence rules separately from the next scheduled occurrence and a ringing/snoozed occurrence. Room v2 migration and durable schedule/cancel replay implemented; full device coverage remains a release gate. |
| 5 (next) | Settings and alarm sounds | Settings screen with a persisted default alarm sound, title, explicit preview/stop, and system alarm-tone selection. Preview stops on leaving the screen. First increment uses a global default resolved at ringing start; changing settings does not switch an already ringing alarm. Fall back to a known playable sound on missing/unreadable selection. Per-alarm overrides and imported audio are later options. |

ADR-23 implements the recurrence/occurrence model, Room migration, outbox replay and weekday UI with the accepted time-zone/DST policy. Reordering features does not lower the P0 reliability or audio-recovery release gates.

### Recurrence policy — accepted in ADR-23

Use local wall-clock time and selected weekdays in the device's current time zone; recompute after zone/time changes and reboot. In a DST gap shift forward by the gap duration; in an overlap ring once using the earlier offset. Snooze belongs to the active occurrence and does not shift the weekly rule. Dismiss completes that occurrence and keeps the rule enabled. Skip missed occurrences rather than replaying a backlog. JVM tests cover zone changes, spring gaps, autumn overlaps, midnight snooze and a snooze crossing a regular slot. Physical device clock/zone/reboot coverage remains outstanding.

Compute and register one next exact occurrence with setAlarmClock rather than using an inexact repeating OS alarm. Keep the existing absolute-instant behavior for one-time alarms until an explicit product decision changes it.
