package com.madebygps.dothabits.data

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Instant
import java.time.ZoneId

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val type: String,
    val dailyTarget: Int,
    val scheduleKind: String,
    /** Bit i set = DayOfWeek.of(i + 1) scheduled. */
    val weekdaysMask: Int,
    val perWeek: Int,
    val negative: Boolean,
    /** Comma separated HH:mm. */
    val reminders: String,
    val position: Int,
    val createdOnEpochDay: Long,
    /** TIMED: sessions per day (dailyTarget is minutes per session). */
    @ColumnInfo(defaultValue = "1") val sessions: Int = 1,
)

@Entity(
    tableName = "entries",
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habitId", "epochDay")],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    /** Count/slips, or completed timer credit in seconds. */
    val amount: Long,
    val createdAtMs: Long,
)

@Entity(
    tableName = "timer_sessions",
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("habitId"), Index("state")],
)
data class TimerSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val startMs: Long,
    val endMs: Long?,
    val state: String,
    val bootCount: Int,
    /** SystemClock.elapsedRealtime() at start; monotonic, valid only while [bootCount] matches. */
    val startElapsedMs: Long? = null,
    val limitSeconds: Long,
    val remainingMs: Long,
    val epochDay: Long,
    val generation: Long = 1,
)

/** Cached Health Connect daily step totals so widgets/Glyph can render without HC access. */
@Entity(tableName = "steps_days")
data class StepsDayEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Long,
    val fetchedAtMs: Long,
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY position, id")
    suspend fun habits(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun habit(id: Long): HabitEntity?

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM habits")
    suspend fun maxPosition(): Int

    @Insert suspend fun insert(h: HabitEntity): Long
    @Update suspend fun update(h: HabitEntity)
    @Update suspend fun updateAll(h: List<HabitEntity>)
    @Query("DELETE FROM habits WHERE id = :id") suspend fun delete(id: Long)

    // Entries
    @Query("SELECT * FROM entries") suspend fun allEntries(): List<EntryEntity>
    @Query("SELECT * FROM entries WHERE habitId = :habitId ORDER BY epochDay, id") suspend fun entries(habitId: Long): List<EntryEntity>
    @Query("SELECT * FROM entries WHERE habitId = :habitId AND epochDay = :day ORDER BY id") suspend fun entriesOn(habitId: Long, day: Long): List<EntryEntity>
    @Insert suspend fun insertEntry(e: EntryEntity): Long
    @Query("DELETE FROM entries WHERE id = :id") suspend fun deleteEntry(id: Long)
    @Query("DELETE FROM entries WHERE habitId = :habitId AND epochDay = :day") suspend fun clearDay(habitId: Long, day: Long)

    @Transaction
    suspend fun replaceDay(habitId: Long, day: Long, amount: Long, nowMs: Long) {
        clearDay(habitId, day)
        if (amount != 0L) insertEntry(EntryEntity(habitId = habitId, epochDay = day, amount = amount, createdAtMs = nowMs))
    }

    // Timer sessions
    @Query("SELECT * FROM timer_sessions") suspend fun allSessions(): List<TimerSessionEntity>
    @Query("SELECT * FROM timer_sessions WHERE habitId = :habitId ORDER BY startMs DESC") suspend fun sessions(habitId: Long): List<TimerSessionEntity>
    @Query("SELECT * FROM timer_sessions WHERE state != 'CLOSED'") suspend fun unfinishedSessions(): List<TimerSessionEntity>
    @Query("SELECT * FROM timer_sessions WHERE id = :id") suspend fun session(id: Long): TimerSessionEntity?
    @Insert suspend fun insertSession(s: TimerSessionEntity): Long
    @Update suspend fun updateSession(s: TimerSessionEntity)
    @Query("DELETE FROM timer_sessions WHERE id = :id") suspend fun deleteSession(id: Long)
    // Steps cache
    @Query("SELECT * FROM steps_days") suspend fun allSteps(): List<StepsDayEntity>
    @Upsert suspend fun upsertSteps(rows: List<StepsDayEntity>)
    @Query("DELETE FROM steps_days WHERE epochDay >= :fromEpochDay") suspend fun deleteStepsFrom(fromEpochDay: Long)

    @Transaction
    suspend fun replaceSteps(fromEpochDay: Long, rows: List<StepsDayEntity>) {
        deleteStepsFrom(fromEpochDay)
        upsertSteps(rows)
    }
}

@Database(
    entities = [HabitEntity::class, EntryEntity::class, TimerSessionEntity::class, StepsDayEntity::class],
    version = 5,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3, spec = DotDatabase.DropNotes::class), AutoMigration(from = 3, to = 4)],
)
abstract class DotDatabase : RoomDatabase() {
    abstract fun dao(): HabitDao

    /** Daily notes were removed; v3 drops the table. */
    @DeleteTable(tableName = "notes")
    class DropNotes : AutoMigrationSpec

    companion object {
        /**
         * Legacy pauses were closed intervals, not logical sessions. Rebuild each day's legacy
         * total (manual offsets are relative to recorded time at the correction's creation),
         * then keep only whole completed sessions. The largest stored daily limit is a
         * conservative heuristic: legacy intervals cannot identify each original logical
         * session after duration edits. Unfinished time is dropped, never credited on its own.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = migrate(db, System.currentTimeMillis())
        }

        internal fun migrate(db: SupportSQLiteDatabase, nowMs: Long) {
            val zone = ZoneId.systemDefault()
            db.query("SELECT id, dailyTarget FROM habits WHERE type = 'TIMED'").use { habits ->
                while (habits.moveToNext()) {
                    val id = habits.getLong(0)
                    val configured = habits.getLong(1) * 60
                    val millis = mutableMapOf<Long, Long>()
                    val manual = mutableMapOf<Long, Long>()
                    val correctedAt = mutableMapOf<Long, Long>()
                    val recordedAt = mutableMapOf<Long, Long>()
                    val dayLimit = mutableMapOf<Long, Long>()
                    db.query("SELECT epochDay, amount, createdAtMs FROM entries WHERE habitId = ?", arrayOf(id)).use { entries ->
                        while (entries.moveToNext()) {
                            val day = entries.getLong(0)
                            manual[day] = (manual[day] ?: 0) + entries.getLong(1)
                            recordedAt[day] = maxOf(recordedAt[day] ?: Long.MIN_VALUE, entries.getLong(2))
                            correctedAt[day] = recordedAt.getValue(day)
                        }
                    }
                    db.query("SELECT startMs, endMs, state, lastAliveMs, limitSeconds FROM timer_sessions WHERE habitId = ?", arrayOf(id)).use { sessions ->
                        while (sessions.moveToNext()) {
                            val startMs = sessions.getLong(0)
                            val limitSeconds = if (sessions.isNull(4)) null else sessions.getLong(4)
                            val limitAt = limitSeconds?.let { startMs + it * 1000 } ?: Long.MAX_VALUE
                            val state = sessions.getString(2)
                            val endMs = when (state) {
                                "CLOSED" -> if (sessions.isNull(1)) startMs else sessions.getLong(1)
                                else -> minOf(
                                    correctedAt.values.maxOrNull() ?: startMs,
                                    if (state == "RUNNING") Long.MAX_VALUE else sessions.getLong(3),
                                    limitAt,
                                    nowMs,
                                )
                            }
                            var from = Instant.ofEpochMilli(startMs)
                            val end = Instant.ofEpochMilli(endMs)
                            while (from < end) {
                                val day = from.atZone(zone).toLocalDate()
                                val to = minOf(end, day.plusDays(1).atStartOfDay(zone).toInstant())
                                val key = day.toEpochDay()
                                // Unfinished rows never earn credit themselves, even with a full
                                // heartbeat. A correction proves user intent at its timestamp;
                                // RUNNING then used wall time, review used its last alive checkpoint.
                                val correction = correctedAt[key]
                                val safeEnd = when {
                                    state == "CLOSED" -> to.toEpochMilli()
                                    correction == null -> from.toEpochMilli()
                                    state == "RUNNING" -> minOf(to.toEpochMilli(), correction)
                                    else -> minOf(to.toEpochMilli(), sessions.getLong(3), correction)
                                }
                                val duration = (safeEnd - from.toEpochMilli()).coerceAtLeast(0)
                                if (duration > 0) {
                                    millis[key] = (millis[key] ?: 0) + duration
                                    recordedAt[key] = maxOf(recordedAt[key] ?: Long.MIN_VALUE, safeEnd)
                                    dayLimit[key] = maxOf(dayLimit[key] ?: 0, limitSeconds ?: configured)
                                }
                                from = to
                            }
                        }
                    }
                    db.execSQL("DELETE FROM entries WHERE habitId = ?", arrayOf(id))
                    (millis.keys + manual.keys).forEach { day ->
                        val total = ((millis[day] ?: 0) / 1000 + (manual[day] ?: 0)).coerceAtLeast(0)
                        val session = dayLimit[day]
                        // Manual-only days were written as whole sessions; keep them as recorded.
                        val credit = if (millis[day] == null) total
                        else if (session == null || session <= 0) 0 else total / session * session
                        if (credit > 0) db.execSQL(
                            "INSERT INTO entries (habitId, epochDay, amount, createdAtMs) VALUES (?, ?, ?, ?)",
                            arrayOf(id, day, credit, recordedAt.getValue(day)),
                        )
                    }
                }
            }
            db.execSQL("DROP TABLE timer_sessions")
            db.execSQL("""CREATE TABLE IF NOT EXISTS timer_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, habitId INTEGER NOT NULL,
                startMs INTEGER NOT NULL, endMs INTEGER, state TEXT NOT NULL, bootCount INTEGER NOT NULL,
                startElapsedMs INTEGER, limitSeconds INTEGER NOT NULL, remainingMs INTEGER NOT NULL,
                epochDay INTEGER NOT NULL, generation INTEGER NOT NULL,
                FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_timer_sessions_habitId ON timer_sessions(habitId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_timer_sessions_state ON timer_sessions(state)")
        }
    }
}
