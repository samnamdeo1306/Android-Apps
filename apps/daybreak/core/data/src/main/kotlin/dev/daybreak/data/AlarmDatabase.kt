package dev.daybreak.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.daybreak.domain.*
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String,
    val label: String,
    val triggerAtMillis: Long,
    val challengeId: String,
    val question: String,
    val answerDigest: String,
    val enabled: Boolean,
    @ColumnInfo(defaultValue = "0") val repeatDays: Int = 0,
    @ColumnInfo(defaultValue = "-1") val repeatMinute: Int = -1,
    @ColumnInfo(defaultValue = "'SCHEDULED'") val phase: String = "SCHEDULED",
    @ColumnInfo(defaultValue = "0") val occurrenceAtMillis: Long = triggerAtMillis,
)

/** No FK: a cancellation tombstone must survive deletion of its alarm. */
@Entity(tableName = "schedule_commands")
data class ScheduleCommandEntity(@PrimaryKey val alarmId: String, val token: String)

data class PendingScheduleEntity(
    @Embedded val command: ScheduleCommandEntity,
    @Relation(parentColumn = "alarmId", entityColumn = "id") val alarm: AlarmEntity?,
)

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY triggerAtMillis") fun observe(): kotlinx.coroutines.flow.Flow<List<AlarmEntity>>
    @Query("SELECT * FROM alarms WHERE id = :id") suspend fun get(id: String): AlarmEntity?
    @Upsert suspend fun upsert(alarm: AlarmEntity)
    @Upsert suspend fun enqueue(command: ScheduleCommandEntity)
    @Query("DELETE FROM alarms WHERE id = :id") suspend fun remove(id: String)
    @Query("DELETE FROM schedule_commands WHERE alarmId = :id AND token = :token") suspend fun acknowledge(id: String, token: String)
    @Query("SELECT token FROM schedule_commands ORDER BY alarmId") fun scheduleChanges(): kotlinx.coroutines.flow.Flow<List<String>>
    @Transaction @Query("SELECT * FROM schedule_commands ORDER BY alarmId") suspend fun pending(): List<PendingScheduleEntity>

    @Transaction suspend fun save(alarm: AlarmEntity) {
        upsert(alarm)
        enqueue(ScheduleCommandEntity(alarm.id, UUID.randomUUID().toString()))
    }
    @Transaction suspend fun delete(id: String) {
        remove(id)
        enqueue(ScheduleCommandEntity(id, UUID.randomUUID().toString()))
    }
    @Transaction suspend fun replace(expected: AlarmEntity, changed: AlarmEntity): Boolean {
        if (get(expected.id) != expected) return false
        save(changed)
        return true
    }
    @Transaction suspend fun deleteIfUnchanged(expected: AlarmEntity): Boolean {
        if (get(expected.id) != expected) return false
        delete(expected.id)
        return true
    }
}

@Database(entities = [AlarmEntity::class, ScheduleCommandEntity::class], version = 2, exportSchema = true)
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN repeatDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarms ADD COLUMN repeatMinute INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE alarms ADD COLUMN phase TEXT NOT NULL DEFAULT 'SCHEDULED'")
                db.execSQL("ALTER TABLE alarms ADD COLUMN occurrenceAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE alarms SET occurrenceAtMillis = triggerAtMillis, phase = CASE WHEN enabled = 0 THEN 'COMPLETED' ELSE 'SCHEDULED' END")
                db.execSQL("CREATE TABLE IF NOT EXISTS schedule_commands (alarmId TEXT NOT NULL PRIMARY KEY, token TEXT NOT NULL)")
                db.execSQL("INSERT INTO schedule_commands (alarmId, token) SELECT id, 'migration-' || id FROM alarms")
            }
        }
    }
}

class RoomAlarmRepository(private val dao: AlarmDao) : AlarmRepository {
    override fun observe() = dao.observe().map { rows -> rows.map { it.toDomain() } }
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override suspend fun save(alarm: Alarm) = dao.save(alarm.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun replace(expected: Alarm, changed: Alarm) = dao.replace(expected.toEntity(), changed.toEntity())
    override suspend fun deleteIfUnchanged(expected: Alarm) = dao.deleteIfUnchanged(expected.toEntity())
    override fun scheduleChanges() = dao.scheduleChanges().map { Unit }
    override suspend fun pendingSchedules() = dao.pending().map { PendingSchedule(it.command.alarmId, it.command.token, it.alarm?.toDomain()) }
    override suspend fun acknowledgeSchedule(alarmId: String, token: String) = dao.acknowledge(alarmId, token)

    private fun Alarm.toEntity() = AlarmEntity(id, label, triggerAtMillis, challengeId, question, answerDigest, enabled,
        repeat?.days?.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) } ?: 0,
        repeat?.time?.let { it.hour * 60 + it.minute } ?: -1,
        (if (enabled) phase else AlarmPhase.COMPLETED).name, occurrenceAtMillis)
    private fun AlarmEntity.toDomain(): Alarm {
        require(repeatDays in 0..127)
        val days = DayOfWeek.entries.filter { repeatDays and (1 shl (it.value - 1)) != 0 }.toSet()
        require(if (days.isEmpty()) repeatMinute == -1 else repeatMinute in 0..1439)
        val rule = if (days.isEmpty()) null else RepeatRule(LocalTime.of(repeatMinute / 60, repeatMinute % 60), days)
        return Alarm(id, label, triggerAtMillis, challengeId, question, answerDigest, enabled, rule,
            AlarmPhase.valueOf(phase), occurrenceAtMillis)
    }
}
