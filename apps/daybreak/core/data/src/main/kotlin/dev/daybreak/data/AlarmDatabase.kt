package dev.daybreak.data

import androidx.room.*
import dev.daybreak.domain.Alarm
import dev.daybreak.domain.AlarmRepository
import kotlinx.coroutines.flow.map

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String,
    val label: String,
    val triggerAtMillis: Long,
    val challengeId: String,
    val question: String,
    val answerDigest: String,
    val enabled: Boolean,
)

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY triggerAtMillis") fun observe(): kotlinx.coroutines.flow.Flow<List<AlarmEntity>>
    @Query("SELECT * FROM alarms WHERE id = :id") suspend fun get(id: String): AlarmEntity?
    @Upsert suspend fun save(alarm: AlarmEntity)
    @Query("DELETE FROM alarms WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [AlarmEntity::class], version = 1, exportSchema = true)
abstract class AlarmDatabase : RoomDatabase() { abstract fun alarms(): AlarmDao }

class RoomAlarmRepository(private val dao: AlarmDao) : AlarmRepository {
    override fun observe() = dao.observe().map { rows -> rows.map { it.toDomain() } }
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override suspend fun save(alarm: Alarm) = dao.save(AlarmEntity(alarm.id, alarm.label,
        alarm.triggerAtMillis, alarm.challengeId, alarm.question, alarm.answerDigest, alarm.enabled))
    override suspend fun delete(id: String) = dao.delete(id)
    private fun AlarmEntity.toDomain() = Alarm(id, label, triggerAtMillis, challengeId, question, answerDigest, enabled)
}
