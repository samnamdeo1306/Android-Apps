# Permissions, privacy, and operational boundaries

## Permission model

- `SCHEDULE_EXACT_ALARM`: user-controlled special access on Android 12+. Checked before scheduling. Revocation can cancel pending exact alarms; regrant triggers future restoration where Android sends the broadcast.
- `POST_NOTIFICATIONS`: runtime access on Android 13+. UI requests it explicitly. Denial affects visibility; the app does not claim denial is harmless.
- `USE_FULL_SCREEN_INTENT`: user/store-controlled access on recent Android. Full-screen presentation is conditional, never guaranteed. A normal notification is the navigation path when permitted.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: foreground audio ownership with a visible notification. Verify current Android and Play restrictions before publishing.
- `WAKE_LOCK`: MediaPlayer can hold CPU wakefulness while sound plays.
- `RECEIVE_BOOT_COMPLETED`: reschedule future alarms after reboot. Room credential-encrypted storage is available after unlock; direct-boot delivery is not implemented.

No Internet, microphone, location, contacts, camera, or storage permission is requested by the app.

## Guarantees and limitations

The in-app completion flow requires a challenge for snooze and dismiss. This is a behavior rule, not a security boundary against the device owner. Force stop, uninstall, shutdown, alarm volume, system controls, battery restrictions, and OEM policies can stop/prevent alarms. Full-screen notifications do not give unrestricted background activity-launch rights.

The prototype uses the system default alarm URI. Audio initialization/playback failures do not yet have robust fallback/retry/vibration recovery. Zero alarm volume can be silent. Do not rely on this app for critical appointments until device tests and hardening are complete.

Room and AlarmManager cannot commit atomically. Crash windows exist during create, snooze, and delete. The active ID is best-effort state rather than a transactionally persisted session. Overlap deferral is not durable. See the P0 roadmap.

## Data handling

Everything stays on the device. No analytics or remote logs. Alarm questions are plaintext; normalized answers are salted SHA-256 digests, not encrypted passwords. Do not use secrets for questions/answers. Android backup is disabled. Deleting an alarm removes its row; forensic erasure from SQLite storage is not promised. Uninstall clears app data according to Android behavior.

## Development and release operations

1. Build/test/lint with the pinned Gradle wrapper and JDK 21.
2. Review Room schema diffs; add migrations before changing an existing schema version.
3. Execute the device checklist and record API/device/results.
4. Review full-screen and foreground-service declarations against current Play policy.
5. Sign releases with a key kept outside the repository; no signing setup or store deployment is included.
6. Update changelog and known limitations. Tag a release only after validation; no tags or pushes are created automatically here.

For alarm failures, record device/API, permission/channel state, alarm ID/time, time zone, app state, and steps. Never attach personal question answers or credential files. Logs should report operational events without private content when observability is added.
