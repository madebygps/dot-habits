package com.madebygps.dothabits.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
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
import kotlinx.coroutines.flow.Flow

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
    val lastAliveMs: Long,
    val bootCount: Int,
)

/** Cached Health Connect daily step totals so widgets/Glyph can render without HC access. */
@Entity(tableName = "steps_days")
data class StepsDayEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Long,
    val fetchedAtMs: Long,
)

@Entity(
    tableName = "notes",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habitId"], onDelete = ForeignKey.CASCADE)],
)
data class NoteEntity(
    val habitId: Long,
    val epochDay: Long,
    @ColumnInfo(name = "text") val text: String,
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY position, id")
    fun observeHabits(): Flow<List<HabitEntity>>

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
    @Query("SELECT * FROM entries") fun observeEntries(): Flow<List<EntryEntity>>
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
    @Query("SELECT * FROM timer_sessions") fun observeSessions(): Flow<List<TimerSessionEntity>>
    @Query("SELECT * FROM timer_sessions WHERE habitId = :habitId ORDER BY startMs DESC") suspend fun sessions(habitId: Long): List<TimerSessionEntity>
    @Query("SELECT * FROM timer_sessions WHERE state = 'RUNNING'") suspend fun runningSessions(): List<TimerSessionEntity>
    @Query("SELECT * FROM timer_sessions WHERE state = 'NEEDS_REVIEW'") suspend fun reviewSessions(): List<TimerSessionEntity>
    @Query("SELECT * FROM timer_sessions WHERE id = :id") suspend fun session(id: Long): TimerSessionEntity?
    @Insert suspend fun insertSession(s: TimerSessionEntity): Long
    @Update suspend fun updateSession(s: TimerSessionEntity)
    @Query("DELETE FROM timer_sessions WHERE id = :id") suspend fun deleteSession(id: Long)
    @Query("UPDATE timer_sessions SET lastAliveMs = :nowMs WHERE state = 'RUNNING' AND bootCount = :bootCount")
    suspend fun touchRunning(nowMs: Long, bootCount: Int)

    // Steps cache
    @Query("SELECT * FROM steps_days") fun observeSteps(): Flow<List<StepsDayEntity>>
    @Upsert suspend fun upsertSteps(rows: List<StepsDayEntity>)

    // Notes
    @Query("SELECT * FROM notes WHERE habitId = :habitId ORDER BY epochDay DESC") fun observeNotes(habitId: Long): Flow<List<NoteEntity>>
    @Upsert suspend fun upsertNote(n: NoteEntity)
    @Query("DELETE FROM notes WHERE habitId = :habitId AND epochDay = :day") suspend fun deleteNote(habitId: Long, day: Long)
}

@Database(
    entities = [HabitEntity::class, EntryEntity::class, TimerSessionEntity::class, StepsDayEntity::class, NoteEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DotDatabase : RoomDatabase() {
    abstract fun dao(): HabitDao
}
