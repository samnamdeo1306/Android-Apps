package dev.daybreak.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import dev.daybreak.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val active = MutableStateFlow<String?>(null)
    private val repository = Repository()
    private val scheduler = Scheduler()
    private var stops = 0
    private var models = 0
    private val alarm = Alarm(id = "alarm", label = "Wake", triggerAtMillis = 1,
        challengeId = "question.v1", question = "Ready?", answerDigest = answerDigest("yes", "alarm"))

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun <T : ViewModel> keep(model: T): T { store.put("model${models++}", model); return model }
    private fun create() = keep(AlarmEditorViewModel(CreateAlarm(repository, scheduler, ChallengeRegistry()),
        UpdateAlarm(repository, scheduler, ChallengeRegistry(), { active.value }), active)).apply {
        begin("Morning ritual")
    }
    private fun ringing() = keep(RingingViewModel(repository, ChallengeRegistry(),
        CompleteAlarmChallenge(repository, scheduler), active, AlarmPlayback { id ->
            assertEquals(active.value, id); stops++; active.value = null
        }))

    @Test fun createGuardsDoubleTapAndSignalsSuccessWithoutCallback() = runTest(dispatcher) {
        val model = create()
        model.save(); model.save()
        assertTrue(model.state.value.busy)
        runCurrent()
        assertEquals(1, repository.saves)
        assertEquals(1, scheduler.schedules)
        assertTrue(model.state.value.saved)
        assertFalse(model.state.value.busy)
        model.saveHandled()
        assertFalse(model.state.value.saved)
    }

    @Test fun failedCreatePreservesDraftAndAllowsRetry() = runTest(dispatcher) {
        val model = create()
        val draft = AlarmDraft(label = "My alarm", challengeId = "question.v1", question = "Ready?", answer = "yes")
        model.updateDraft(draft)
        scheduler.denied = true
        model.save(); runCurrent()
        assertFalse(model.state.value.saved)
        assertFalse(model.state.value.busy)
        assertNotNull(model.state.value.message)
        assertEquals(draft, model.state.value.draft)
        assertTrue(repository.rows.value.isEmpty())
        scheduler.denied = false
        model.save(); runCurrent()
        assertTrue(model.state.value.saved)
        assertEquals("", model.state.value.draft.answer)
    }

    @Test fun listObservesRowsAndRefusesActiveDeletion() = runTest(dispatcher) {
        repository.rows.value = listOf(alarm)
        active.value = alarm.id
        val model = keep(AlarmListViewModel(repository, DeleteAlarm(repository, scheduler), active))
        runCurrent()
        assertEquals(listOf(alarm), model.state.value.alarms)
        model.delete(alarm); runCurrent()
        assertEquals(listOf(alarm), repository.rows.value)
        assertEquals(0, scheduler.cancels)
        assertNotNull(model.state.value.message)
        active.value = null
        model.delete(alarm); model.delete(alarm); runCurrent()
        assertTrue(model.state.value.alarms.isEmpty())
        assertEquals(1, scheduler.cancels)
    }

    @Test fun wrongAnswersNeverStopPlaybackForEitherAction() = runTest(dispatcher) {
        repository.rows.value = listOf(alarm); active.value = alarm.id
        val model = ringing(); runCurrent()
        val prompt = model.state.value.ringing?.prompt
        AlarmAction.entries.forEach {
            model.submit(it, "wrong"); runCurrent()
            assertFalse(model.state.value.busy)
            assertEquals(R.string.error_wrong_answer, model.state.value.message)
            assertEquals(prompt, model.state.value.ringing?.prompt)
        }
        assertEquals(0, stops)
        assertEquals(0, repository.saves)
        assertEquals(0, scheduler.schedules)
        assertEquals(0, scheduler.cancels)
    }

    @Test fun deniedSnoozeKeepsRingingAndCorrectRetryStopsOnce() = runTest(dispatcher) {
        repository.rows.value = listOf(alarm); active.value = alarm.id
        val model = ringing(); runCurrent()
        scheduler.denied = true
        model.submit(AlarmAction.SNOOZE, "yes"); runCurrent()
        assertEquals(0, stops)
        assertEquals(alarm.id, active.value)
        assertEquals(alarm, repository.rows.value.single())
        scheduler.denied = false
        model.submit(AlarmAction.SNOOZE, "yes")
        model.submit(AlarmAction.SNOOZE, "yes")
        runCurrent()
        assertEquals(1, stops)
        assertEquals(dev.daybreak.domain.AlarmPhase.SNOOZED, repository.rows.value.single().phase)
        assertNull(active.value)
    }

    @Test fun dismissStopsOnlyAfterPersistenceAndCancellation() = runTest(dispatcher) {
        repository.rows.value = listOf(alarm); active.value = alarm.id
        val model = ringing(); runCurrent()
        repository.failSave = true
        model.submit(AlarmAction.DISMISS, "yes"); runCurrent()
        assertEquals(0, stops)
        assertEquals(R.string.error_complete_alarm, model.state.value.message)
        repository.failSave = false
        model.submit(AlarmAction.DISMISS, "yes"); runCurrent()
        assertEquals(false, repository.rows.value.single().enabled)
        assertEquals(1, scheduler.cancels)
        assertEquals(1, stops)
    }

    @Test fun unknownChallengeFailsClosedAndNextActiveAlarmCanLoad() = runTest(dispatcher) {
        repository.rows.value = listOf(alarm.copy(challengeId = "unknown")); active.value = alarm.id
        val model = ringing(); runCurrent()
        assertTrue(model.state.value.active)
        assertNull(model.state.value.ringing)
        assertEquals(R.string.error_unsupported_challenge, model.state.value.message)
        model.submit(AlarmAction.DISMISS, "yes"); runCurrent()
        assertEquals(0, stops)
        active.value = null; runCurrent()
        repository.rows.value = listOf(alarm); active.value = alarm.id; runCurrent()
        assertNotNull(model.state.value.ringing)
    }

    @Test fun oldMessageAcknowledgementDoesNotClearNewMessage() = runTest(dispatcher) {
        val model = create()
        model.updateDraft(AlarmDraft(label = "")); model.save(); runCurrent()
        val old = requireNotNull(model.state.value.message)
        model.updateDraft(AlarmDraft(label = "Wake", time = "bad")); model.save(); runCurrent()
        val current = model.state.value.message
        assertNotEquals(old, current)
        model.messageShown(old)
        assertEquals(current, model.state.value.message)
    }

    @Test fun schedulerDenialMapsTypedFailureWithoutDisplayingExceptionText() = runTest(dispatcher) {
        val denied = object : AlarmScheduler {
            override fun schedule(alarm: Alarm) { throw AlarmFailure(AlarmFailureReason.EXACT_ACCESS_DENIED) }
            override fun cancel(id: String) = Unit
        }
        val model = keep(AlarmEditorViewModel(CreateAlarm(repository, denied, ChallengeRegistry()),
            UpdateAlarm(repository, denied, ChallengeRegistry(), { active.value }), active))
        model.begin("Wake")
        model.save(); runCurrent()
        assertEquals(R.string.error_exact_access, model.state.value.message)
        assertFalse(model.state.value.saved)
        assertTrue(repository.rows.value.isEmpty())
    }

    @Test fun unknownStorageFailureUsesSafePresentationFallback() = runTest(dispatcher) {
        val model = create()
        repository.failSave = true
        model.save(); runCurrent()
        assertEquals(R.string.error_create_alarm, model.state.value.message)
        assertFalse(model.state.value.saved)
    }

    @Test fun editPrefillsWithoutRecoveringPlaintextAndCancelLeavesPersistenceAlone() = runTest(dispatcher) {
        repository.rows.value = listOf(alarm)
        val model = create()
        model.edit(alarm)
        assertTrue(model.state.value.visible)
        assertEquals(alarm, model.state.value.original)
        assertEquals(alarm.label, model.state.value.draft.label)
        assertEquals(alarm.question, model.state.value.draft.question)
        assertEquals("", model.state.value.draft.answer)
        model.updateDraft(model.state.value.draft.copy(label = "Changed", answer = "private"))
        model.close()
        assertFalse(model.state.value.visible)
        assertEquals("", model.state.value.draft.answer)
        assertEquals(listOf(alarm), repository.rows.value)
        assertEquals(0, repository.saves)
        assertEquals(0, scheduler.schedules)
    }

    @Test fun editSaveGuardsDoubleTapPreservesIdAndClosesEditor() = runTest(dispatcher) {
        repository.rows.value = listOf(alarm)
        val model = create(); model.edit(alarm)
        model.updateDraft(model.state.value.draft.copy(label = "Edited"))
        model.save(); model.save(); runCurrent()
        assertTrue(model.state.value.saved)
        assertFalse(model.state.value.visible)
        assertEquals(R.string.alarm_updated, model.state.value.message)
        assertEquals("Edited", repository.rows.value.single().label)
        assertEquals(alarm.id, repository.rows.value.single().id)
        assertEquals(alarm.answerDigest, repository.rows.value.single().answerDigest)
        assertEquals(1, scheduler.schedules)
    }

    @Test fun editFailureRetainsDraftAndHasNoSuccessState() = runTest(dispatcher) {
        repository.rows.value = listOf(alarm)
        val model = create(); model.edit(alarm)
        val draft = model.state.value.draft.copy(question = "New?")
        model.updateDraft(draft)
        model.save(); runCurrent()
        assertTrue(model.state.value.visible)
        assertFalse(model.state.value.saved)
        assertEquals(draft, model.state.value.draft)
        assertEquals(R.string.error_question_answer, model.state.value.message)
        assertEquals(listOf(alarm), repository.rows.value)
    }

    @Test fun alarmBecomingActiveAfterEditorOpenedIsRejectedAtSave() = runTest(dispatcher) {
        repository.rows.value = listOf(alarm)
        val model = create(); model.edit(alarm)
        active.value = alarm.id
        model.save(); runCurrent()
        assertFalse(model.state.value.saved)
        assertEquals(R.string.error_active_alarm, model.state.value.message)
        assertEquals(0, repository.saves)
        model.close(); model.edit(alarm)
        assertFalse(model.state.value.visible)
    }

    @Test fun editorDoesNotOverwriteAnAlarmChangedSinceOpening() = runTest(dispatcher) {
        repository.rows.value = listOf(alarm)
        val model = create(); model.edit(alarm)
        val changed = alarm.copy(label = "Another update")
        repository.rows.value = listOf(changed)
        model.save(); runCurrent()
        assertEquals(R.string.error_stale_alarm, model.state.value.message)
        assertEquals(listOf(changed), repository.rows.value)
        assertFalse(model.state.value.saved)
    }

    @Test fun recurringDraftIsSavedAndEditingASnoozeUsesTheRuleTime() = runTest(dispatcher) {
        val model = create()
        val days = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY)
        model.updateDraft(model.state.value.draft.copy(time = "07:30", repeatDays = days))
        model.save(); runCurrent()
        val saved = repository.rows.value.single()
        assertEquals(days, saved.repeat?.days)
        assertEquals(java.time.LocalTime.of(7, 30), saved.repeat?.time)
        val snoozed = saved.copy(triggerAtMillis = System.currentTimeMillis() + 300_000,
            phase = dev.daybreak.domain.AlarmPhase.SNOOZED)
        repository.rows.value = listOf(snoozed)
        model.edit(snoozed)
        assertEquals("07:30", model.state.value.draft.time)
        assertEquals(days, model.state.value.draft.repeatDays)
        model.updateDraft(model.state.value.draft.copy(label = "Renamed"))
        model.save(); runCurrent()
        assertEquals(snoozed.triggerAtMillis, repository.rows.value.single().triggerAtMillis)
        assertEquals(snoozed.phase, repository.rows.value.single().phase)
    }

    @Test fun emptyRepeatSelectionCreatesOneTimeAlarmAndCancelDoesNotPersistRuleEdits() = runTest(dispatcher) {
        val model = create()
        model.save(); runCurrent()
        val once = repository.rows.value.single()
        assertNull(once.repeat)
        model.edit(once)
        model.updateDraft(model.state.value.draft.copy(repeatDays = setOf(java.time.DayOfWeek.SUNDAY)))
        model.close()
        assertEquals(once, repository.rows.value.single())
    }

    private class Repository : AlarmRepository {
        val rows = MutableStateFlow<List<Alarm>>(emptyList())
        var saves = 0
        var failSave = false
        override fun observe() = rows.asStateFlow()
        override suspend fun get(id: String) = rows.value.find { it.id == id }
        override suspend fun save(alarm: Alarm) {
            check(!failSave) { "Storage unavailable" }
            saves++
            rows.value = rows.value.filterNot { it.id == alarm.id } + alarm
        }
        override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
    }
    private class Scheduler : AlarmScheduler {
        var denied = false
        var schedules = 0
        var cancels = 0
        override fun schedule(alarm: Alarm) { check(!denied) { "Access denied" }; schedules++ }
        override fun cancel(id: String) { cancels++ }
    }
}
