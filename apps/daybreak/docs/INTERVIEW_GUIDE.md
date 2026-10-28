# Design review walkthrough

## Five-minute narrative

1. **Problem:** sleepy taps defeat normal alarms; add an intentional challenge for both exit actions.
2. **Invariant:** wrong answers cannot reach scheduling/cancel/persistence side effects. Show `CompleteAlarmChallenge` and its tests.
3. **Flow:** Compose sends a command to its screen-specific ViewModel, which publishes immutable state; the use case verifies; ports apply the action; Android service stops only after success.
4. **Boundaries:** pure Kotlin domain, Room adapter, Android UI/delivery. Explain why only three modules.
5. **Extensibility:** show the registry extension test; distinguish text challenges from future camera/motion UI requirements.
6. **Android reality:** explain exact access, foreground sound, full-screen permission, reboot restoration, process death and force-stop limits.
7. **Tradeoff:** acknowledge non-atomic Room/AlarmManager operations and show the implemented desired-state outbox and its remaining recovery limits (ADR-23).

## Questions to prepare for

| Question | Defensible answer |
| --- | --- |
| Why not WorkManager? | It schedules deferrable work, not an exact user-facing wake-up alarm |
| Why Flow? | Room emits list changes; lifecycle-aware collection keeps UI state current without manual reloads |
| Why a service? | Playback lifetime must survive activity recreation and backgrounding |
| Why not a DI framework? | A single explicit composition root is sufficient now; constructor ports retain testability |
| How do you add a puzzle? | Strategy + registry + tests; richer input adds a renderer/config contract, not scheduler branches |
| Does SHA-256 secure user secrets? | No. It avoids plaintext retention but is guessable; personal secrets are out of scope |
| Is scheduling transactional? | No. Room and AlarmManager are separate systems; Room atomically journals desired state, then an idempotent reconciler applies it; OS effects remain separate |
| What did you actually validate? | Show build/test/lint reports and separately identify unrun device tests |
| What happens after process death? | Persisted RINGING phase supports best-effort service recreation; the outbox replays desired schedules, but generated prompts are not durable |
| Why not claim production ready? | OS delivery, audio failure recovery and broader physical-device coverage still need evidence; v1→v2 migration is tested |

## Demo script

Grant required access, create a question alarm a couple of minutes ahead, show its Room-backed list entry, background/lock the phone, open the ringing challenge, submit a wrong answer, then a normalized correct answer. Demonstrate both dismiss and snooze in separate runs. Show unit tests and a fake additional strategy. Keep a known-good device and enough time for the five-minute snooze; do not fake a successful delivery in a recorded demo.

## Delivery hardening walkthrough

Show ADR-21 and the API 30 connected test report. Explain why AlarmManager's receiver wake lock needs a bounded handoff, why foreground startup and first ringing alert have separate notification IDs, and why channel importance/full-screen access are checked independently. Demonstrate that the ViewModel never launches background playback and notification actions cannot bypass the common challenge gate. Distinguish a successful forced-idle emulator test from physical secure-keyguard/OEM and Android 14–16 permission coverage still required before release.

## Recurrence walkthrough — ADR-23

Explain the difference between the weekly local-time rule and its current delivery/occurrence anchor. Snooze moves only the current delivery; dismiss advances the weekly rule. Travel uses the phone zone, gaps shift forward and overlaps ring once. Show Clock-based tests instead of waiting a week in a demo.

Show a Room transaction writing desired alarm state plus a tokenized schedule command. Applying AlarmManager and acknowledging the token happen separately. Replaying after a crash is idempotent; a stale acknowledgement cannot remove a newer command, and deletion retains a cancellation tombstone. This supports eventual convergence, not exactly-once delivery or a cross-system transaction. Point to the real migration/outbox tests and distinguish them from remaining physical recovery evidence. Persistent prompts, audio fallback and a richer overlap queue remain honest limitations.
