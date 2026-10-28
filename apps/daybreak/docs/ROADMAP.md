# Prioritized improvements

## P0 — reliability before real-world release

- Persist explicit scheduled/ringing/snoozed/completed states and sessions; reconcile desired schedules with OS registrations on startup and permission changes. Use stable IDs and idempotent commands; add crash-window tests.
- Introduce a durable overlapping-alarm queue instead of one-minute best-effort deferral.
- Add audio fallback, playback error handling, clear volume/channel diagnostics, and optional vibration. Verify service restart and wakefulness behavior on physical devices.
- Add Room and Android integration tests, then complete the device acceptance matrix.
- Design a safe recovery path for unknown/inaccessible challenge types without creating an accidental ordinary dismiss bypass.

## P1 — useful product features

- Recurring weekdays, edit/enable controls, and explicit time-zone/DST behavior. These are more valuable next than adding many puzzles.
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

These are planned increments beyond the current one-time-alarm baseline, not implemented capabilities. ADR-16's screen-specific ViewModels are now implemented, giving the editor and settings clear state ownership. The default English localization foundation (ADR-18) is now implemented. The proper time picker (ADR-19) is now implemented. Editing existing alarms is now implemented (ADR-20), with best-effort compensation; durable reconciliation remains P0. Next: full-screen and idle delivery hardening. Translations and visual pseudolocale/RTL acceptance remain follow-up work.

| Order | Increment | Acceptance and dependencies |
| --- | --- | --- |
| Done (foundation) | String localization foundation | Move app-owned user-facing text to Android string/plural resources, including Compose labels, accessibility descriptions, validation feedback, challenge titles/generated prompts, notification text and channel descriptions. Use format arguments instead of concatenation; respect locale and system time format. Map typed domain failures and prompt data to localized text in the Android presentation layer, keeping domain free of Android resources. Preserve user-entered labels/questions verbatim and stable challenge IDs/answer normalization. Verify resource completeness, pseudolocales, RTL, and large fonts. Start with default English resources; additional translated languages are separate increments. |
| Done (implementation) | Proper time picker | Material time picker as the primary input, system 12/24-hour preference, accessible keyboard input fallback; cancel leaves the draft unchanged. Share between create and edit. |
| Done (implementation) | Edit existing alarms | Open a prefilled editor; save preserves alarm ID and replaces pending delivery; cancel changes nothing. Blank personal answer means retain the stored digest for an unchanged question; changing the question requires a new answer. Reject edits while that alarm is ringing. Scheduling failure/compensation tests included; durable reconciliation/crash-window tests remain P0. |
| 3 | Full-screen and idle delivery hardening | Exercise existing full-screen notification path on locked/screen-off devices and during Doze; test permission denied and channel disabled paths. On unlocked devices accept Android's heads-up presentation. Keep challenge access through the notification and playback in the service. |
| 4 | Settings and alarm sounds | Settings screen with a persisted default alarm sound, title, explicit preview/stop, and system alarm-tone selection. Preview stops on leaving the screen. First increment uses a global default resolved at ringing start; changing settings does not switch an already ringing alarm. Fall back to a known playable sound on missing/unreadable selection. Per-alarm overrides and imported audio are later options. |
| 5 | Recurring alarms | Select weekdays plus once-only mode; show next occurrence. Persist recurrence rules separately from the next scheduled occurrence and a ringing/snoozed occurrence. Requires a Room migration and durable reconciliation before enabling. |

### Recurrence policy proposal — not yet accepted

Use local wall-clock time and selected weekdays in the device's current time zone; recompute after zone/time changes and reboot. In a DST gap use the next valid local time; in an overlap ring once using the earlier offset. Snooze belongs to the active occurrence and does not shift the weekly rule. Dismiss completes that occurrence and keeps the rule enabled. Skip missed occurrences rather than replaying a backlog. Test a zone change, spring gap, autumn overlap, midnight snooze, and a snooze overlapping the next occurrence before accepting this policy.

Compute and register one next exact occurrence with setAlarmClock rather than using an inexact repeating OS alarm. Keep the existing absolute-instant behavior for one-time alarms until an explicit product decision changes it.
