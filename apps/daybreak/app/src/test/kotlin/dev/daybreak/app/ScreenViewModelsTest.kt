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
    private fun create() = keep(CreateAlarmViewModel(CreateAlarm(repository, scheduler, ChallengeRegistry()))).apply {
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
        assertTrue(model.state.value.created)
        assertFalse(model.state.value.busy)
        model.creationHandled()
        assertFalse(model.state.value.created)
    }

    @Test fun failedCreatePreservesDraftAndAllowsRetry() = runTest(dispatcher) {
        val model = create()
        val draft = AlarmDraft(label = "My alarm", challengeId = "question.v1", question = "Ready?", answer = "yes")
        model.updateDraft(draft)
        scheduler.denied = true
        model.save(); runCurrent()
        assertFalse(model.state.value.created)
        assertFalse(model.state.value.busy)
        assertNotNull(model.state.value.message)
        assertEquals(draft, model.state.value.draft)
        assertTrue(repository.rows.value.isEmpty())
        scheduler.denied = false
        model.save(); runCurrent()
        assertTrue(model.state.value.created)
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
        assertEquals(0, repository.saves)
        scheduler.denied = false
        model.submit(AlarmAction.SNOOZE, "yes")
        model.submit(AlarmAction.SNOOZE, "yes")
        runCurrent()
        assertEquals(1, stops)
        assertEquals(1, repository.saves)
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
        val model = keep(CreateAlarmViewModel(CreateAlarm(repository, denied, ChallengeRegistry())))
        model.begin("Wake")
        model.save(); runCurrent()
        assertEquals(R.string.error_exact_access, model.state.value.message)
        assertFalse(model.state.value.created)
        assertTrue(repository.rows.value.isEmpty())
    }

    @Test fun unknownStorageFailureUsesSafePresentationFallback() = runTest(dispatcher) {
        val model = create()
        repository.failSave = true
        model.save(); runCurrent()
        assertEquals(R.string.error_create_alarm, model.state.value.message)
        assertFalse(model.state.value.created)
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
