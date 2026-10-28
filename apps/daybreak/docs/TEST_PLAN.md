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
