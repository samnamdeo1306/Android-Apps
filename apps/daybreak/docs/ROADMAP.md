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

## What makes the interview project stronger

Show a complete reliable vertical slice, an honest device test report, a small decision history, and a demonstration of adding one challenge without changing the scheduler. A long list of libraries or unfinished puzzle screens is less defensible than clear invariants and measured reliability.
