# Design review walkthrough

## Five-minute narrative

1. **Problem:** sleepy taps defeat normal alarms; add an intentional challenge for both exit actions.
2. **Invariant:** wrong answers cannot reach scheduling/cancel/persistence side effects. Show `CompleteAlarmChallenge` and its tests.
3. **Flow:** Compose submits an intent to the ViewModel; the use case verifies; ports apply the action; Android service stops only after success.
4. **Boundaries:** pure Kotlin domain, Room adapter, Android UI/delivery. Explain why only three modules.
5. **Extensibility:** show the registry extension test; distinguish text challenges from future camera/motion UI requirements.
6. **Android reality:** explain exact access, foreground sound, full-screen permission, reboot restoration, process death and force-stop limits.
7. **Tradeoff:** acknowledge non-atomic Room/AlarmManager operations and describe a durable reconciliation design as the next step.

## Questions to prepare for

| Question | Defensible answer |
| --- | --- |
| Why not WorkManager? | It schedules deferrable work, not an exact user-facing wake-up alarm |
| Why Flow? | Room emits list changes; lifecycle-aware collection keeps UI state current without manual reloads |
| Why a service? | Playback lifetime must survive activity recreation and backgrounding |
| Why not a DI framework? | A single explicit composition root is sufficient now; constructor ports retain testability |
| How do you add a puzzle? | Strategy + registry + tests; richer input adds a renderer/config contract, not scheduler branches |
| Does SHA-256 secure user secrets? | No. It avoids plaintext retention but is guessable; personal secrets are out of scope |
| Is scheduling transactional? | No. Room and AlarmManager are separate systems; a durable desired-state reconciler is planned |
| What did you actually validate? | Show build/test/lint reports and separately identify unrun device tests |
| What happens after process death? | Best-effort active ID/service recreation; session durability and crash reconciliation remain hardening work |
| Why not claim production ready? | OS delivery, audio failure recovery, migrations, and physical-device coverage still need evidence |

## Demo script

Grant required access, create a question alarm a couple of minutes ahead, show its Room-backed list entry, background/lock the phone, open the ringing challenge, submit a wrong answer, then a normalized correct answer. Demonstrate both dismiss and snooze in separate runs. Show unit tests and a fake additional strategy. Keep a known-good device and enough time for the five-minute snooze; do not fake a successful delivery in a recorded demo.
