package dev.daybreak.app.alarm

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.daybreak.data.AlarmDatabase
import dev.daybreak.data.RoomAlarmRepository
import dev.daybreak.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class RecurrencePersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun realV1SchemaMigratesWithoutChangingLegacyIdsAnswersOrInstants() = runBlocking {
        val name = "migration-test-${java.util.UUID.randomUUID()}.db"
        var room: AlarmDatabase? = null
        try {
            val schema = JSONObject(InstrumentationRegistry.getInstrumentation().context.assets
                .open("dev.daybreak.data.AlarmDatabase/1.json").bufferedReader().use { it.readText() })
                .getJSONObject("database")
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    sqlite.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", entity.getString("tableName")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
                sqlite.execSQL("INSERT INTO alarms (id,label,triggerAtMillis,challengeId,question,answerDigest,enabled) VALUES (?,?,?,?,?,?,?)",
                    arrayOf<Any?>("legacy", "Wake", 1_900_000_000_000L, "question.v1", "Question?", "opaque-digest", 1))
                sqlite.execSQL("INSERT INTO alarms (id,label,triggerAtMillis,challengeId,question,answerDigest,enabled) VALUES (?,?,?,?,?,?,?)",
                    arrayOf<Any?>("completed", "Old", 1L, "sum.v1", "", "", 0))
                sqlite.version = 1
            }
            room = Room.databaseBuilder(context, AlarmDatabase::class.java, name)
                .addMigrations(AlarmDatabase.MIGRATION_1_2).build()
            val repository = RoomAlarmRepository(room.alarms())
            val legacy = repository.get("legacy")!!
            assertEquals(1_900_000_000_000L, legacy.triggerAtMillis)
            assertEquals(legacy.triggerAtMillis, legacy.occurrenceAtMillis)
            assertEquals("opaque-digest", legacy.answerDigest)
            assertEquals("Question?", legacy.question)
            assertNull(legacy.repeat)
            assertEquals(AlarmPhase.SCHEDULED, legacy.phase)
            assertEquals(AlarmPhase.COMPLETED, repository.get("completed")!!.phase)
            assertEquals(2, repository.pendingSchedules().size)
        } finally { room?.close(); context.deleteDatabase(name) }
    }

    @Test fun weeklyRuleOccurrenceAndJournalAreAtomicAndOldAcknowledgementCannotEraseNewWork() = runBlocking {
        val room = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java).build()
        try {
            val repository = RoomAlarmRepository(room.alarms())
            val alarm = Alarm(id = "weekly", label = "Wake", triggerAtMillis = 1_900_000_000_000,
                challengeId = "sum.v1", repeat = RepeatRule(LocalTime.of(7, 30), setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
            repository.save(alarm)
            assertEquals(alarm, repository.get(alarm.id))
            val old = repository.pendingSchedules().single()
            assertEquals(alarm, old.alarm)
            val snoozed = alarm.copy(triggerAtMillis = alarm.triggerAtMillis + 300_000, phase = AlarmPhase.SNOOZED)
            assertTrue(repository.replace(alarm, snoozed))
            assertFalse(repository.replace(alarm, alarm.copy(label = "Stale")))
            repository.acknowledgeSchedule(old.alarmId, old.token)
            assertEquals(snoozed, repository.pendingSchedules().single().alarm)
            assertEquals(alarm.occurrenceAtMillis, repository.get(alarm.id)!!.occurrenceAtMillis)
            assertFalse(repository.deleteIfUnchanged(alarm))
            assertTrue(repository.deleteIfUnchanged(snoozed))
            val deleted = repository.pendingSchedules().single()
            assertNull(deleted.alarm)
            repository.acknowledgeSchedule(deleted.alarmId, deleted.token)
            assertTrue(repository.pendingSchedules().isEmpty())
        } finally { room.close() }
    }
}
