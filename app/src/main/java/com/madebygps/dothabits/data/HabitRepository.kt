package com.madebygps.dothabits.data

import android.content.ContentResolver
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.room.withTransaction
import com.madebygps.dothabits.domain.CompletionPolicy
import com.madebygps.dothabits.domain.Habit
import com.madebygps.dothabits.domain.HabitHistory
import com.madebygps.dothabits.domain.HabitType
import com.madebygps.dothabits.domain.HistoryAssembler
import com.madebygps.dothabits.domain.HoldAction
import com.madebygps.dothabits.domain.Schedule
import com.madebygps.dothabits.domain.SessionState
import com.madebygps.dothabits.domain.TimerMath
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
    private val database: DotDatabase,
    private val settingsStore: SettingsStore,
    private val contentResolver: ContentResolver,
) {
    private val dao = database.dao()
    /** Invoked after every write so widgets, reminders, notifications and the Glyph refresh. */
    var onDataChanged: (suspend () -> Unit)? = null

    /** Invoked with the habit ids whose timer just stopped at the end of a session. */
    var onSessionsFinished: (suspend (List<Long>) -> Unit)? = null

    private val writeLock = Mutex()

    val raw: Flow<RawData> = combine(
        database.invalidationTracker.createFlow("habits", "entries", "timer_sessions", "steps_days"),
        settingsStore.settings,
    ) { _, settings ->
        // Completion changes both sessions and credit. Never expose a half-old combined snapshot.
        database.withTransaction {
            RawData(dao.habits(), dao.allEntries(), dao.allSessions(), dao.allSteps(), settings)
        }
    }

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun bootCount(): Int = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun clockNow() = TimerMath.ClockReading(System.currentTimeMillis(), SystemClock.elapsedRealtime(), bootCount())

    fun histories(data: RawData, now: Instant = Instant.now()): List<HabitHistory> {
        val zone = zone()
        return HistoryAssembler.assemble(
            habits = data.habits.map { it.toDomain() },
            entries = data.entries.map { it.toDomain() },
            sessions = clockNow().let { c ->
                data.sessions.filter { it.state == SessionState.CLOSED.name || it.bootCount == c.bootCount }
                    .map { TimerMath.project(it.toDomain(), c) }
            },
            stepsByDay = data.steps.associate { LocalDate.ofEpochDay(it.epochDay) to it.steps },
            today = now.atZone(zone).toLocalDate(),
            zone = zone,
            now = now,
        )
    }

    fun snapshot(data: RawData, now: Instant = Instant.now(), preferredTimer: Long? = null): TodaySnapshot =
        SnapshotBuilder.build(histories(data, now), now.atZone(zone()).toLocalDate(), data.settings.weekStart, now, preferredTimer)

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

    suspend fun currentSnapshot(preferredTimer: Long? = null): TodaySnapshot = snapshot(raw.first(), preferredTimer = preferredTimer)

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
                database.withTransaction {
                    if (habit.type != HabitType.TIMED)
                        dao.unfinishedSessions().filter { it.habitId == habit.id }.forEach { dao.deleteSession(it.id) }
                    dao.update(habit.toEntity())
                    discardCompletedTimersLocked(clockNow())
                }
                habit.id
            }
        }
        changed()
        return id
    }

    suspend fun deleteHabit(id: Long) {
        writeLock.withLock { dao.delete(id) }
        changed()
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
            Habit(name = "Read 1 hour", icon = "book", type = HabitType.TIMED, dailyTarget = 60, sessions = 1, createdOn = today),
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

    /** Press-and-hold on a habit tile in the app. */
    suspend fun hold(habitId: Long): HoldResult {
        val snap = currentSnapshot()
        val t = snap.habits.firstOrNull { it.habit.id == habitId } ?: return HoldResult.AlreadyDone
        return when (CompletionPolicy.holdAction(t)) {
            HoldAction.LOG_ONE -> {
                val id = dao.insertEntry(EntryEntity(habitId = habitId, epochDay = snap.date.toEpochDay(), amount = 1, createdAtMs = System.currentTimeMillis()))
                changed()
                HoldResult.Logged(id, t.habit.name)
            }
            HoldAction.TOGGLE_TIMER -> when (toggleTimer(habitId)) {
                true -> HoldResult.TimerStarted(t.habit.name)
                false -> HoldResult.TimerPaused(t.habit.name)
                null -> HoldResult.AlreadyDone
            }
            HoldAction.NONE_ALREADY_DONE -> HoldResult.AlreadyDone
            HoldAction.NONE_AUTOMATIC -> HoldResult.Automatic
        }
    }

    suspend fun undoEntry(entryId: Long) {
        dao.deleteEntry(entryId); changed()
    }

    /** History edit / backfill for counts and slips only. */
    suspend fun setManualAmount(habitId: Long, date: LocalDate, amount: Long) {
        writeLock.withLock {
            require(!date.isAfter(LocalDate.now(zone()))) { "Cannot log the future" }
            val habit = dao.habit(habitId)?.toDomain() ?: return
            require(habit.type == HabitType.COUNT)
            dao.replaceDay(habitId, date.toEpochDay(), amount.coerceAtLeast(0), System.currentTimeMillis())
        }
        changed()
    }

    /** Absolute completed credit edit; meeting today's goal discards unfinished time without credit. */
    suspend fun setTimerCompletions(habitId: Long, date: LocalDate, completedSessions: Long) {
        writeLock.withLock {
            database.withTransaction {
                val clock = clockNow()
                val now = Instant.ofEpochMilli(clock.wallMs)
                require(!date.isAfter(now.atZone(zone()).toLocalDate())) { "Cannot log the future" }
                val habit = dao.habit(habitId)?.toDomain() ?: return@withTransaction
                require(habit.type == HabitType.TIMED)
                require(completedSessions >= 0)
                dao.replaceDay(habitId, date.toEpochDay(), Math.multiplyExact(completedSessions, habit.sessionSeconds), clock.wallMs)
                discardCompletedTimersLocked(clock)
            }
        }
        changed()
    }

    suspend fun manualAmount(habitId: Long, date: LocalDate): Long =
        dao.entriesOn(habitId, date.toEpochDay()).sumOf { it.amount }

    // ---- Timers ---------------------------------------------------------------------------

    /**
     * Resume the same planned session, or create one with the current configured duration.
     * Only one timer runs at a time; switching pauses the previous logical session.
     */
    suspend fun startTimer(habitId: Long) {
        changeTimer(habitId, start = true)
    }

    suspend fun pauseTimer(habitId: Long, sessionId: Long? = null, generation: Long? = null) {
        changeTimer(habitId, start = false, sessionId = sessionId, generation = generation)
    }

    /** Returns true when started, false when paused, or null if unavailable or today's goal is met. */
    suspend fun toggleTimer(habitId: Long): Boolean? = changeTimer(habitId, start = null)

    private data class TimerChange(val started: Boolean?, val stateChanged: Boolean, val finished: List<Long>)

    private suspend fun changeTimer(habitId: Long, start: Boolean?, sessionId: Long? = null, generation: Long? = null): Boolean? {
        val result = writeLock.withLock {
            database.withTransaction {
                val clock = clockNow()
                if (sessionId != null) {
                    val expected = dao.session(sessionId)?.toDomain()
                    if (expected == null || generation == null ||
                        !TimerMath.matchesCallback(expected, sessionId, generation) || expected.habitId != habitId)
                        return@withTransaction TimerChange(null, false, emptyList())
                }
                val maintained = maintainTimersLocked(clock)
                val habit = dao.habit(habitId)?.toDomain()
                if (habit == null || habit.type != HabitType.TIMED || habit.sessionSeconds <= 0) {
                    Log.w("DotHabitsTimer", "Ignoring timer control for non-timed or missing habit id=$habitId")
                    return@withTransaction TimerChange(null, maintained.stateChanged, maintained.finished)
                }
                val unfinished = dao.unfinishedSessions().map { it.toDomain() }
                val ownRun = unfinished.firstOrNull { it.habitId == habitId && it.state == SessionState.RUNNING }
                val shouldStart = start ?: (ownRun == null)
                val today = Instant.ofEpochMilli(clock.wallMs).atZone(zone()).toLocalDate()
                val completed = dao.entriesOn(habitId, today.toEpochDay()).sumOf { it.amount }
                if (shouldStart && CompletionPolicy.timerGoalMet(habit, completed))
                    return@withTransaction TimerChange(null, maintained.stateChanged, maintained.finished)
                if (shouldStart && ownRun == null) {
                    val plan = TimerMath.startSession(habitId, habit.sessionSeconds, unfinished, clock, zone())
                    plan.updates.forEach { dao.updateSession(it.toEntity()) }
                    plan.newSession?.let { dao.insertSession(it.toEntity()) }
                } else if (!shouldStart && ownRun != null) {
                    dao.updateSession(TimerMath.pause(ownRun, clock).toEntity())
                }
                TimerChange(shouldStart, maintained.stateChanged || (shouldStart != (ownRun != null)), maintained.finished)
            }
        }
        if (result.stateChanged) changed()
        if (result.finished.isNotEmpty()) onSessionsFinished?.invoke(result.finished)
        return result.started
    }

    private data class TimerMaintenance(val stateChanged: Boolean, val finished: List<Long>)

    private suspend fun discardCompletedTimersLocked(clock: TimerMath.ClockReading): Boolean {
        val today = Instant.ofEpochMilli(clock.wallMs).atZone(zone()).toLocalDate()
        var discarded = false
        dao.unfinishedSessions().forEach { row ->
            val habit = dao.habit(row.habitId)?.toDomain() ?: return@forEach
            val completed = dao.entriesOn(habit.id, today.toEpochDay()).sumOf { it.amount }
            if (CompletionPolicy.shouldDiscardTimer(habit, completed, row.toDomain(), today)) {
                dao.deleteSession(row.id)
                discarded = true
            }
        }
        return discarded
    }

    private suspend fun maintainTimersLocked(clock: TimerMath.ClockReading): TimerMaintenance {
        val finished = mutableListOf<Long>()
        var stateChanged = discardCompletedTimersLocked(clock)
        dao.unfinishedSessions().forEach { row ->
            val s = row.toDomain()
            val change = TimerMath.settle(s, clock, zone())
            if (change.session != s) {
                if (change.session == null) dao.deleteSession(s.id)
                else dao.updateSession(change.session.toEntity())
                stateChanged = true
            }
            change.credit?.let { credit ->
                dao.insertEntry(EntryEntity(habitId = credit.habitId, epochDay = credit.date.toEpochDay(),
                    amount = credit.amount, createdAtMs = credit.createdAt.toEpochMilli()))
                finished.add(s.habitId)
            }
        }
        if (discardCompletedTimersLocked(clock)) stateChanged = true
        return TimerMaintenance(stateChanged, finished)
    }

    /** Shared lifecycle entry point, also used by timer commands inside their transaction. */
    suspend fun maintainTimers(refreshSurfaces: Boolean = true, sessionId: Long? = null, generation: Long? = null): List<Long> {
        val result = writeLock.withLock {
            database.withTransaction {
                if (sessionId != null) {
                    val s = dao.session(sessionId)?.toDomain()
                    if (s == null || generation == null || !TimerMath.matchesCallback(s, sessionId, generation))
                        return@withTransaction TimerMaintenance(false, emptyList())
                }
                maintainTimersLocked(clockNow())
            }
        }
        if (result.stateChanged && refreshSurfaces) changed()
        if (result.finished.isNotEmpty()) onSessionsFinished?.invoke(result.finished)
        return result.finished
    }

    suspend fun sessions(habitId: Long) = dao.sessions(habitId).map { it.toDomain() }

    // ---- Steps cache ----------------------------------------------------------------------

    /** Replaces cached steps from [from] onward with a successful read, so removed data doesn't linger. */
    suspend fun cacheSteps(from: LocalDate, byDay: Map<LocalDate, Long>): Boolean {
        // Skip unchanged totals so frequent foreground reads don't redraw widgets for nothing.
        val cached = raw.first().steps.filter { it.epochDay >= from.toEpochDay() }
            .associate { LocalDate.ofEpochDay(it.epochDay) to it.steps }
        if (cached == byDay) return false
        val now = System.currentTimeMillis()
        dao.replaceSteps(from.toEpochDay(), byDay.map { (d, s) -> StepsDayEntity(d.toEpochDay(), s, now) })
        changed()
        return true
    }
}
