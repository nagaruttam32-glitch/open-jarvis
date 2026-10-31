package com.openjarvis.automation

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val command: String,
    val scheduleType: String,
    val scheduleHour: Int = 0,
    val scheduleMinute: Int = 0,
    val scheduleDayOfWeek: Int = 0,
    val scheduleIntervalMs: Long = 0,
    val enabled: Boolean = true,
    val lastRun: Long? = null,
    val lastResult: String? = null,
    val runCount: Int = 0
)

@Dao
interface AutomationEntityDao {
    @Query("SELECT * FROM automations ORDER BY name")
    suspend fun getAll(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: String): AutomationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AutomationEntity)

    @Update
    suspend fun update(entity: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: String)
}

class AutomationDao(private val d: AutomationEntityDao) {
    suspend fun getAll(): List<AutomationManager.Automation> =
        d.getAll().map { it.toAutomation() }

    suspend fun getById(id: String): AutomationManager.Automation? =
        d.getById(id)?.toAutomation()

    suspend fun insert(automation: AutomationManager.Automation) =
        d.insert(automation.toEntity())

    suspend fun update(automation: AutomationManager.Automation) =
        d.update(automation.toEntity())

    suspend fun delete(id: String) = d.delete(id)
}

private fun AutomationManager.Automation.toEntity(): AutomationEntity {
    var type = "daily"
    var h = 0
    var m = 0
    var dow = 0
    var ms = 0L
    when (val s = schedule) {
        is AutomationManager.AutomationSchedule.Daily -> {
            type = "daily"; h = s.hour; m = s.minute
        }
        is AutomationManager.AutomationSchedule.Weekly -> {
            type = "weekly"
            val (d1, h1, m1) = s
            dow = d1; h = h1; m = m1
        }
        is AutomationManager.AutomationSchedule.Interval -> {
            type = "interval"; ms = s.intervalMs
        }
        is AutomationManager.AutomationSchedule.Once -> {
            type = "once"; ms = s.atMs
        }
        else -> {}
    }
    return AutomationEntity(id, name, command, type, h, m, dow, ms, enabled, lastRun, lastResult, runCount)
}

private fun AutomationEntity.toAutomation(): AutomationManager.Automation {
    val s: AutomationManager.AutomationSchedule = when (scheduleType) {
        "weekly" -> AutomationManager.AutomationSchedule.Weekly(scheduleDayOfWeek, scheduleHour, scheduleMinute)
        "interval" -> AutomationManager.AutomationSchedule.Interval(scheduleIntervalMs)
        "once" -> AutomationManager.AutomationSchedule.Once(scheduleIntervalMs)
        else -> AutomationManager.AutomationSchedule.Daily(scheduleHour, scheduleMinute)
    }
    return AutomationManager.Automation(id, name, command, s, enabled, lastRun, lastResult, runCount)
}

@Database(entities = [AutomationEntity::class], version = 1, exportSchema = false)
abstract class AutomationDB : RoomDatabase() {
    abstract fun entityDao(): AutomationEntityDao

    fun automationDao(): AutomationDao = AutomationDao(entityDao())

    companion object {
        @Volatile
        private var INSTANCE: AutomationDB? = null

        fun getInstance(context: Context): AutomationDB {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AutomationDB::class.java,
                    "automations.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class AutomationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("automation_id") ?: return Result.failure()

        return try {
            val dao = AutomationDB.getInstance(applicationContext).automationDao()
            val automation = dao.getById(id) ?: return Result.failure()

            kotlinx.coroutines.delay(2000)

            val updated = automation.copy(
                lastRun = System.currentTimeMillis(),
                lastResult = "success",
                runCount = automation.runCount + 1
            )
            dao.update(updated)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
