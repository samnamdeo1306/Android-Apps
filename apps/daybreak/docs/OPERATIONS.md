# Permissions, privacy, and operational boundaries

## Permission model

- `SCHEDULE_EXACT_ALARM`: user-controlled special access on Android 12+. Checked before scheduling. Revocation can cancel pending exact alarms; regrant triggers future restoration where Android sends the broadcast.
- `POST_NOTIFICATIONS`: runtime access on Android 13+. UI requests it explicitly. Denial affects visibility; the app does not claim denial is harmless.
- `USE_FULL_SCREEN_INTENT`: user/store-controlled access on recent Android. Full-screen presentation is conditional, never guaranteed. A normal notification is the navigation path when permitted.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: foreground audio ownership with a visible notification. Verify current Android and Play restrictions before publishing.
- `WAKE_LOCK`: Bounded receiver/service startup locks bridge to MediaPlayer wakefulness during playback (ADR-21). Startup locks time out after 30 seconds and release on success/failure/destruction.
- `RECEIVE_BOOT_COMPLETED`: reschedule future alarms after reboot. Room credential-encrypted storage is available after unlock; direct-boot delivery is not implemented.

No Internet, microphone, location, contacts, camera, or storage permission is requested by the app.

## Guarantees and limitations

The in-app completion flow requires a challenge for snooze and dismiss. This is a behavior rule, not a security boundary against the device owner. Force stop, uninstall, shutdown, alarm volume, system controls, battery restrictions, and OEM policies can stop/prevent alarms. Full-screen notifications do not give unrestricted background activity-launch rights.

The prototype uses the system default alarm URI. Audio initialization/playback failures update the ongoing notification with failure feedback and retain challenge access, but do not yet have robust fallback/retry/vibration recovery. Zero alarm volume can be silent. Do not rely on this app for critical appointments until device tests and hardening are complete.

Room and AlarmManager cannot commit atomically. Crash windows exist during create, snooze, and delete. Room v2 persists occurrence phases and an atomic schedule/cancel outbox. The active ID is a process-local UI cache; generated challenge prompts are not durable. Deferred occurrence time is persisted, but overlap handling is not a durable multi-alarm queue. See the P0 roadmap.

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

## Delivery presentation and setup

The stable ringing notification channel is created on application startup with high importance and no channel sound; playback belongs to the service. User channel changes are respected. Home setup diagnostics link to channel settings when importance is below High, in addition to notification and full-screen permission actions. Disabled notifications/channel can remove the notification navigation path even though foreground playback may continue. Re-enable the relevant access and verify an alarm on the device; the app does not force permissions or bypass system controls.

The service immediately enters foreground mode with a non-full-screen loading notification. Only a loaded enabled alarm claiming the active slot receives a ringing alert/full-screen request. Invalid or overlapping delivery never intentionally wakes a different challenge. Startup wake locks are bounded energy protection, not durable recovery. Service-start failures are logged and release the receiver lock; automatic recovery is not implemented. API 35+ full-screen PendingIntent creator options are explicit, and show-when-locked/turn-screen-on window flags are limited to active alarms. Secure-keyguard access does not unlock the phone or guarantee the system will launch an activity.

## Recurrence, migration and schedule recovery

Weekly alarms follow the current phone zone and rebase regular future occurrences on startup/time/zone/reboot/access changes. One-time instants and future snoozes/deferrals remain absolute. Boot skips powered-off ringing/missed occurrences and advances repeats, rather than playing a backlog. Ordinary process recovery retains RINGING; foreground app recovery may restart the service. Prompts can regenerate after process death. See ADR-23 for DST, coalescing and the 60-second foreground handoff grace.

Room migration 1→2 retains old alarms as one-time records, including their IDs/answer digests/triggers. Back up development databases before schema experimentation; no destructive fallback exists. Durable scheduling commands survive save/delete interruption. Failed OS application remains pending for idempotent replay on the next recovery trigger; the setup card shows a retry action. A failed compensation or storage error can still leave temporary playback/desired-state disagreement. Force stop prevents background recovery, and physical reboot/permission/zone/OEM evidence remains required before release.
