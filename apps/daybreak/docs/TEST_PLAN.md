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
