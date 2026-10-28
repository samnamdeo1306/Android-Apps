# Validation strategy

## Automated development gate

```sh
./gradlew :core:domain:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Domain tests exercise question normalization, wrong/blank answers, deterministic puzzle generation, malformed puzzle input, registry extension and duplicate IDs, per-alarm salting, both action gates, five-minute timing from an injected clock, cancellation on dismiss, and revoked scheduling permission. These tests use fake ports and do not start Android.

`assembleDebug` checks resource/manifest merging, Room code generation, Kotlin/Compose compilation and APK packaging. Lint checks Android API and platform usage. Neither is a device acceptance test.

Generated reports: `core/domain/build/test-results/test/`, `core/domain/build/reports/tests/test/`, `app/build/reports/lint-results-debug.html`. Record actual current-run counts and results in release notes; do not equate zero tests with a pass.

## Physical-device acceptance matrix — required before release

Use at least API 26, 31, 33, 34, and 36, plus a restrictive OEM device. This matrix is a plan until results are recorded.

| Scenario | Expected |
| --- | --- |
| Exact access denied | Cannot create; actionable settings link; no phantom enabled alarm |
| Exact access revoked after scheduling | Explain access on return; snooze failure keeps ringing; no success claim |
| Notifications denied / channel disabled | Clear setup guidance; inspect actual background delivery limitations |
| Full-screen access denied | Notification opens challenge; do not expect automatic screen presentation |
| Question wrong / blank / normalized correct | Wrong/blank never stops sound; normalized correct permits selected action |
| Puzzle incorrect / malformed / correct | Same action gate behavior |
| Correct snooze | Sound stops after scheduling; same alarm rings about five minutes later with a new session |
| Correct dismiss | Sound stops and no future pending delivery remains |
| Rotate / background / screen locked | Sound continues; current ViewModel prompt survives rotation |
| Process killed while ringing | Inspect best-effort service recreation and active ID; record gaps |
| Force stop / power off | Document Android limitation; app cannot guarantee delivery |
| Boot with future/past enabled alarms | Future restored; powered-off missed alarms disabled |
| Two overlapping alarms | Active challenge retained; second deferred one minute; verify persistence failures |
| Time zone / manual clock / DST | Verify absolute-instant policy; no claim of recurring wall-clock behavior |
| Alarm volume zero / DND / unavailable sound URI | Identify silent failure; audio fallback is a release blocker |
| TalkBack / 200% font / landscape / keyboard | Labels readable, content reachable, focus order understandable |
| Delete pending alarm | OS delivery canceled; record removed |

## Next automated suites

Room in-memory DAO/invalidation tests and schema migration tests; service integration tests; Compose semantics/action tests; scheduler PendingIntent identity tests. Add these around meaningful behavior, not tests that merely repeat implementation.

## Definition of done

A development increment needs passing domain tests, APK build, lint, reviewed changes, and updated docs. A production release also needs completed device acceptance, accessibility and audio recovery review, release signing, current target/API policy review, and all P0 roadmap items. Current limitations must remain visible even when build checks pass.

## Screen ViewModel behavior tests

ScreenViewModelsTest runs on the JVM with a controlled Main coroutine dispatcher and fake repository/scheduler/playback ports. It verifies duplicate create/delete/submit guards, failed creation compensation and draft retention, active-alarm deletion rejection, wrong answers for both actions, snooze denial/retry, persistence failure before dismiss shutdown, unsupported challenges failing closed with later-session recovery, and conditional message acknowledgement. ViewModelStore cleanup cancels collectors after each test. These tests do not instantiate Android services or verify Compose layout, rotation on a device, or notification delivery.

## Localization validation

Default resources live in app/src/main/res/values/strings.xml. Resource compilation and lint check references/formatting. JVM tests verify typed feedback for wrong answers, unsupported challenges, and exact-access denial; unexpected storage exceptions use generic copy. Domain tests exercise one puzzle session across an Arabic display-locale change (including localized digits) and personal question/answer matching under Turkish locale. These tests do not prove Compose layout or live Android configuration handling.

Debug builds generate en-XA (expanded/accented) and ar-XB (RTL) pseudolocales. Device checklist: open list, create form, both challenge types and notifications in each pseudolocale; inspect clipping, scroll reachability, placeholders, counts (0/1/many), 200% fonts, date/time formatting and 12/24-hour preference. Change locale while a puzzle is ringing: operands, entered answer, digest and playback must survive; only app-owned wording changes. Preserve personal questions verbatim. Confirm snackbar wording resolves in the new locale and notification/channel wording on next service start. Human translations and full accessibility sign-off remain outstanding.

2026-10-28 localization smoke check: debug build installed on the running Pixel 3a API 30 emulator. English home/list and creation dialog reviewed, including the device's 12-hour time display. Dialog closed without saving. This does not complete the pseudolocale/RTL/large-font/live-switch checklist above.

## Time-picker acceptance — ADR-19

2026-10-28: Pixel 3a API 30 emulator, English, default font size, 12-hour system preference. Reviewed the rounded time card and dial dialog. Changed pending hour from 07 to 03, cancelled, and verified the card remained 7:00 AM. Switched to keyboard mode, selected 09:09 PM, confirmed, and verified the draft card showed 9:09 PM. No alarm was scheduled. The palette was refined after review to keep selected time/period controls peach/mint rather than the default Material purple.

Automated development gate: existing 16 domain + 10 ViewModel tests pass; assembleDebug and lintDebug succeed. These suites protect business rules but do not automate the new picker UI. Manual checks still required: 24-hour mode, midnight/noon, Back/outside dismissal, rotation with pending edits, large fonts/landscape, TalkBack, RTL/pseudolocales, and scheduling the selected value end-to-end. Short/large-font configurations default to input mode and allow switching to the dial; confirm layout reachability on those configurations before accessibility sign-off.

Final palette check: installed the refined APK and visually confirmed peach selected time/dial controls and mint AM/PM selection on the same API 30 emulator. The picker is left open for inspection.

## Alarm editing acceptance — ADR-20

Automated: UpdateAlarmTest adds 15 domain behavior cases for ID/digest retention, changed questions, challenge switches, next occurrence/completed alarms, missing/stale/active protection, scheduling/restoration failures, activation during persistence, and cancellation compensation. ScreenViewModelsTest adds 5 editor cases for prefill/cancel, duplicate save, failure state, activation after opening and stale snapshots. Development gate passed with 31 domain + 15 ViewModel tests, zero failures/errors/skips; assembleDebug and lintDebug succeeded. Final lint contains dependency-update warnings only. The picker compact-mode decision now uses current window height rather than deprecated screen-height assumptions.

2026-10-28 emulator review: Pixel 3a API 30, English/default font, existing completed addition alarm. Confirmed Edit action placement, prefilled name/time/challenge, shared time card, the Save & schedule label and rescheduling explanation. Closed the editor without saving. On-device update delivery identity, personal-answer retention, permission-denial/recovery, rotation and near-trigger races remain manual checks; domain/ViewModel tests are not device-delivery evidence. Durable reconciliation and crash-window tests remain P0 release-hardening work.

## Android delivery instrumentation — ADR-21

Run on the prepared emulator:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.daybreak.app.alarm.AlarmDeliveryTest
```

The emulator-only end-to-end case turns the screen off, forces Doze with shell commands and asserts deep IDLE, then creates a unique temporary Room row and schedules setAlarmClock from that state. It waits for the foreground alarm notification and active ID, and checks screen wake/MainActivity resumption when full-screen capability is available. Both wrong-answer actions must leave the record and active ID unchanged; a correct answer uses the shared completion use case. Cleanup removes only its test alarm, stops its test playback, clears its active ID, unforces idle, resets the simulated battery and wakes the emulator. The test skips if the target is not an emulator, an alarm is already active, or required exact/notification access is absent. Report skips separately from passes.

Other Android tests cover notification fallback fields for simulated denied app/channel/full-screen capability, no full-screen intent on loading, immutable ringing intent where supported, channel re-creation preserving importance, independent startup token release and timeout. These are Android framework tests, not a substitute for revoking real permissions on each API. Connected reports live in app/build/reports/androidTests/connected/ and app/build/outputs/androidTest-results/connected/.

Remaining delivery matrix: API 26/31/33/34/35/36 and a restrictive physical OEM; actual notification denial/channel blocking and full-screen special-access denial/regrant; secure keyguard; unlocked heads-up behavior; cold process start, duplicate/overlapping delivery and sticky restart; zero volume/DND/missing audio; physical device wakefulness and audible playback; failure launching foreground service. The available API 30 emulator cannot establish API 34+ permission or API 35/36 PendingIntent creator behavior. Keep these release gates visible after a successful emulator run.

Test setup finding: an imminent alarm-clock registration can prevent Android from entering deep idle at all. The end-to-end test therefore verifies deep IDLE before registering its short-delay alarm; Android may leave idle immediately to prepare delivery. A screen-off test without that precondition is not recorded as Doze evidence. The first stricter precondition run exposed this setup error; the corrected procedure is used for the final reported result.

2026-10-28 final delivery results: 6 Android tests passed on Pixel_3a_API_30 (Android 11/API 30), zero failures/errors/skips. The corrected end-to-end case verified deep IDLE before alarm registration, then observed the ringing foreground notification, screen wake and MainActivity resumption. Both wrong-answer actions were rejected without changing the active row/ID. Correct-answer dismissal used the shared completion use case, followed by test-only cleanup. Post-run checks found no Daybreak service, device idle state ACTIVE and no held Daybreak startup wake lock (historical acquire/release entries are not held locks).

The 31 domain and 15 ViewModel tests also pass. Debug app/test APK builds and lint succeeded; lint contains dependency-update warnings only. The Android tests establish the API 30 path and capability-policy fields, not actual API 34 permission revocation, API 35/36 creator-mode execution, audible sound or physical keyguard/OEM reliability. Those release checks remain outstanding. The final build retains the system alarm small icon suitable for monochrome notification rendering.

## Recurrence acceptance — ADR-23

JVM coverage: weekly day selection, strict next match, spring gap and autumn overlap (including no second-offset replay), travel, midnight/year rollover, repeat creation, snooze crossing a regular slot, stale completion, reboot/missed behavior, timezone rebase versus absolute snooze/one-time triggers, recent-delivery grace, crash-before-ack replay, deletion tombstones and newer-token preservation. Editor tests cover weekly/once selection, cancel and editing a snoozed rule without replacing its rule time with the snooze deadline.

Android coverage: RecurrencePersistenceTest loads the exported v1 schema including its identity metadata, migrates through Room to v2 and checks old IDs/digests/triggers/one-time defaults. Its in-memory Room test verifies repeat/occurrence round-trip, transactional compare-and-write, stale-write rejection, token acknowledgement and deletion tombstones. Existing delivery instrumentation now checks the persisted RINGING claim and still exercises forced-idle delivery and both action gates.

Remaining manual/physical checks: weekly repeat over several days, actual timezone/time changes, reboot while scheduled/ringing/snoozed, real permission revoke/regrant, process kill before/after OS registration and acknowledgement, large-font/RTL weekday controls, challenge-prompt restoration, secure keyguard/OEM/audio reliability. JVM clocks and emulator schema tests do not prove physical scheduling reliability or an atomic Room/AlarmManager transaction.

2026-10-29 final automated results: 49 domain tests, 17 ViewModel tests and 8 Android tests passed, zero failures/errors/skips. Debug app/test APK builds and lint passed; the 16 lint warnings concern dependency updates only. Android results came from Pixel_3a_API_30 (Android 11), including actual v1→v2 migration, Room journal semantics and forced-idle delivery regression. Post-run checks found no Daybreak service or held startup wake lock and device idle state ACTIVE. Schema v1 remains unchanged. These results do not complete the physical/newer-API release matrix above.

2026-10-29 visual smoke check: installed the final debug APK after connected-test cleanup, opened create on Pixel 3a API 30 in English/default font/12-hour mode, selected Monday and observed its selected styling, then chose Once only and verified the weekday cleared. Weekday chips wrapped inside the scrollable editor and the Close action remained reachable. Closed without scheduling an extra alarm. Large fonts, RTL and TalkBack remain unchecked.
