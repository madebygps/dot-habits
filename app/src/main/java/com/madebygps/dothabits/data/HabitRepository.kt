package com.madebygps.dothabits.data

import android.content.ContentResolver
import android.provider.Settings
import com.madebygps.dothabits.domain.CompletionPolicy
import com.madebygps.dothabits.domain.Habit
import com.madebygps.dothabits.domain.HabitHistory
import com.madebygps.dothabits.domain.HabitType
import com.madebygps.dothabits.domain.HistoryAssembler
import com.madebygps.dothabits.domain.HoldAction
import com.madebygps.dothabits.domain.Schedule
import com.madebygps.dothabits.domain.SessionState
import com.madebygps.dothabits.domain.SnapshotBuilder
import com.madebygps.dothabits.domain.TodaySnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

const val MAX_HABITS = 24
const val HABITS_PER_PAGE = 6

/** Raw rows observed together; converted to domain on demand. */
data class RawData(
    val habits: List<HabitEntity>,
    val entries: List<EntryEntity>,
    val sessions: List<TimerSessionEntity>,
    val steps: List<StepsDayEntity>,
    val settings: AppSettings,
)

sealed interface HoldResult {
    data class Logged(val entryId: Long, val habitName: String) : HoldResult
    data class TimerStarted(val habitName: String) : HoldResult
    data class TimerPaused(val habitName: String) : HoldResult
    data object AlreadyDone : HoldResult
    data object Automatic : HoldResult
}

class HabitRepository(
    private val dao: HabitDao,
    private val settingsStore: SettingsStore,
    private val contentResolver: ContentResolver,
) {
    /** Invoked after every write so widgets, reminders, notifications and the Glyph refresh. */
    var onDataChanged: (suspend () -> Unit)? = null

    private val writeLock = Mutex()

    val raw: Flow<RawData> = combine(
        dao.observeHabits(), dao.observeEntries(), dao.observeSessions(), dao.observeSteps(), settingsStore.settings,
    ) { h, e, s, st, set -> RawData(h, e, s, st, set) }

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun bootCount(): Int = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun histories(data: RawData, now: Instant = Instant.now()): List<HabitHistory> {
        val zone = zone()
        return HistoryAssembler.assemble(
            habits = data.habits.map { it.toDomain() },
            entries = data.entries.map { it.toDomain() },
            sessions = data.sessions.map { it.toDomain() },
            stepsByDay = data.steps.associate { LocalDate.ofEpochDay(it.epochDay) to it.steps },
            today = now.atZone(zone).toLocalDate(),
            zone = zone,
            now = now,
        )
    }

    fun snapshot(data: RawData, now: Instant = Instant.now()): TodaySnapshot =
        SnapshotBuilder.build(histories(data, now), now.atZone(zone()).toLocalDate(), data.settings.weekStart, now)

    /** Snapshot that re-evaluates on data change and every [tickMillis] (for running timers / midnight). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun snapshots(tickMillis: Long): Flow<Pair<RawData, TodaySnapshot>> =
        combine(raw, ticker(tickMillis)) { data, now -> data to snapshot(data, now) }

    private fun ticker(period: Long) = flow {
        while (true) {
            emit(Instant.now())
            delay(period)
        }
    }

    suspend fun currentSnapshot(): TodaySnapshot = snapshot(raw.first())

    private suspend fun changed() {
        onDataChanged?.invoke()
    }

    // ---- Habits -------------------------------------------------------------------------

    suspend fun habit(id: Long): Habit? = dao.habit(id)?.toDomain()

    suspend fun saveHabit(habit: Habit): Long {
        val id = writeLock.withLock {
            if (habit.id == 0L) {
                check(dao.count() < MAX_HABITS) { "Limit of $MAX_HABITS habits reached" }
                dao.insert(habit.copy(position = dao.maxPosition() + 1).toEntity())
            } else {
                dao.update(habit.toEntity()); habit.id
            }
        }
        changed()
        return id
    }

    suspend fun deleteHabit(id: Long) {
        dao.delete(id); changed()
    }

    suspend fun move(id: Long, delta: Int) {
        writeLock.withLock {
            val list = dao.habits().toMutableList()
            val i = list.indexOfFirst { it.id == id }
            val j = i + delta
            if (i < 0 || j !in list.indices) return
            val item = list.removeAt(i)
            list.add(j, item)
            dao.updateAll(list.mapIndexed { idx, h -> h.copy(position = idx) })
        }
        changed()
    }

    /** Agreed example habits. No history is fabricated: they start empty today. */
    suspend fun addExampleHabits() {
        val today = LocalDate.now(zone())
        listOf(
            Habit(name = "Creatine", icon = "scoop", type = HabitType.COUNT, dailyTarget = 1, createdOn = today),
            Habit(name = "Workout", icon = "dumbbell", type = HabitType.COUNT, dailyTarget = 1, schedule = Schedule.daysPerWeek(4), createdOn = today),
            Habit(name = "Walk 10,000 steps", icon = "shoe", type = HabitType.STEPS, dailyTarget = 10_000, createdOn = today),
            Habit(name = "Read 1 hour", icon = "book", type = HabitType.TIMED, dailyTarget = 60, createdOn = today),
            Habit(name = "Journal", icon = "pen", type = HabitType.COUNT, dailyTarget = 1, reminders = listOf(LocalTime.of(21, 0)), createdOn = today),
            Habit(name = "Chonk Meds", icon = "paw", type = HabitType.COUNT, dailyTarget = 2, reminders = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), createdOn = today),
        ).forEach { h ->
            writeLock.withLock {
                if (dao.count() < MAX_HABITS) dao.insert(h.copy(position = dao.maxPosition() + 1).toEntity())
            }
        }
        changed()
    }

    // ---- Logging --------------------------------------------------------------------------

    /** Press-and-hold on a circle in the app. */
    suspend fun hold(habitId: Long): HoldResult {
        val snap = currentSnapshot()
        val t = snap.habits.firstOrNull { it.habit.id == habitId } ?: return HoldResult.AlreadyDone
        return when (CompletionPolicy.holdAction(t)) {
            HoldAction.LOG_ONE -> {
                val id = dao.insertEntry(EntryEntity(habitId = habitId, epochDay = snap.date.toEpochDay(), amount = 1, createdAtMs = System.currentTimeMillis()))
                changed()
                HoldResult.Logged(id, t.habit.name)
            }
            HoldAction.TOGGLE_TIMER -> if (t.timerRunning) {
                pauseTimer(habitId); HoldResult.TimerPaused(t.habit.name)
            } else {
                startTimer(habitId); HoldResult.TimerStarted(t.habit.name)
            }
            HoldAction.NONE_ALREADY_DONE -> HoldResult.AlreadyDone
            HoldAction.NONE_AUTOMATIC -> HoldResult.Automatic
        }
    }

    suspend fun undoEntry(entryId: Long) {
        dao.deleteEntry(entryId); changed()
    }

    /** History edit / backfill: replaces manual amount logged on [date] (count, or seconds for timers). */
    suspend fun setManualAmount(habitId: Long, date: LocalDate, amount: Long) {
        require(!date.isAfter(LocalDate.now(zone()))) { "Cannot log the future" }
        dao.replaceDay(habitId, date.toEpochDay(), amount.coerceAtLeast(0), System.currentTimeMillis())
        changed()
    }

    suspend fun manualAmount(habitId: Long, date: LocalDate): Long =
        dao.entriesOn(habitId, date.toEpochDay()).sumOf { it.amount }

    fun notes(habitId: Long) = dao.observeNotes(habitId).map { list -> list.associate { LocalDate.ofEpochDay(it.epochDay) to it.text } }

    suspend fun setNote(habitId: Long, date: LocalDate, text: String) {
        if (text.isBlank()) dao.deleteNote(habitId, date.toEpochDay())
        else dao.upsertNote(NoteEntity(habitId, date.toEpochDay(), text.trim()))
    }

    // ---- Timers ---------------------------------------------------------------------------

    suspend fun startTimer(habitId: Long) {
        writeLock.withLock {
            val now = System.currentTimeMillis()
            dao.runningSessions().forEach { dao.updateSession(it.copy(state = SessionState.CLOSED.name, endMs = now, lastAliveMs = now)) }
            dao.insertSession(TimerSessionEntity(habitId = habitId, startMs = now, endMs = null, state = SessionState.RUNNING.name, lastAliveMs = now, bootCount = bootCount()))
        }
        changed()
    }

    suspend fun pauseTimer(habitId: Long) {
        writeLock.withLock {
            val now = System.currentTimeMillis()
            dao.runningSessions().filter { it.habitId == habitId }
                .forEach { dao.updateSession(it.copy(state = SessionState.CLOSED.name, endMs = now, lastAliveMs = now)) }
        }
        changed()
    }

    suspend fun sessions(habitId: Long) = dao.sessions(habitId).map { it.toDomain() }

    suspend fun deleteSession(id: Long) {
        dao.deleteSession(id); changed()
    }

    /**
     * Reboot detection. Any RUNNING session from an earlier boot is moved to NEEDS_REVIEW;
     * until resolved it only counts up to its last confirmed-alive time (never over-counts).
     * Returns the number of sessions that need review.
     */
    suspend fun reconcileAfterBoot(): Int {
        val boot = bootCount()
        val stale = writeLock.withLock {
            dao.runningSessions().filter { it.bootCount != boot }.onEach {
                dao.updateSession(it.copy(state = SessionState.NEEDS_REVIEW.name))
            }
        }
        if (stale.isNotEmpty()) changed()
        return dao.reviewSessions().size
    }

    /** Record that running sessions are confirmed alive now (any wake-up of the app). */
    suspend fun touchAlive() = dao.touchRunning(System.currentTimeMillis(), bootCount())

    /** Resolve an interrupted session: keep until [end] (clamped to sensible bounds) or discard. */
    suspend fun resolveReview(sessionId: Long, end: Instant?) {
        val s = dao.session(sessionId) ?: return
        if (end == null) dao.deleteSession(sessionId)
        else {
            val endMs = end.toEpochMilli().coerceIn(s.startMs, System.currentTimeMillis())
            dao.updateSession(s.copy(state = SessionState.CLOSED.name, endMs = endMs))
        }
        changed()
    }

    suspend fun pendingReviews() = dao.reviewSessions().map { it.toDomain() }

    // ---- Steps cache ----------------------------------------------------------------------

    suspend fun cacheSteps(byDay: Map<LocalDate, Long>) {
        if (byDay.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertSteps(byDay.map { (d, s) -> StepsDayEntity(d.toEpochDay(), s, now) })
        changed()
    }
}
