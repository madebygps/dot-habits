package com.madebygps.dothabits.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.madebygps.dothabits.data.HoldResult
import com.madebygps.dothabits.data.RawData
import com.madebygps.dothabits.data.StepsStatus
import com.madebygps.dothabits.domain.TodaySnapshot
import com.madebygps.dothabits.dotApp
import com.madebygps.dothabits.system.StepsSyncWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiData(val raw: RawData?, val snapshot: TodaySnapshot)

sealed interface UiEvent {
    data class Logged(val entryId: Long, val name: String) : UiEvent
    data class Message(val text: String) : UiEvent
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dot = app.dotApp
    val repository = dot.repository
    val settingsStore = dot.settings

    /** One-second tick while the UI is visible so running timers count up live. */
    val ui: StateFlow<UiData> = repository.snapshots(tickMillis = 1_000L)
        .let { flow -> kotlinx.coroutines.flow.flow { flow.collect { (raw, snap) -> emit(UiData(raw, snap)) } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiData(null, TodaySnapshot.Empty))

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _steps = MutableStateFlow<StepsStatus?>(null)
    val steps: StateFlow<StepsStatus?> = _steps.asStateFlow()

    fun onResume() = viewModelScope.launch {
        repository.reconcileAfterBoot()
        repository.finishElapsedSessions()
        repository.touchAlive()
        refreshSteps()
    }

    /** Foreground step reads while the app is visible; cancelled when it isn't. */
    private var stepsTicker: kotlinx.coroutines.Job? = null

    fun startStepsTicker() {
        stepsTicker?.cancel()
        stepsTicker = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                runCatching { StepsSyncWorker.syncSteps(dot, days = 1) }
            }
        }
    }

    fun stopStepsTicker() {
        stepsTicker?.cancel()
        stepsTicker = null
    }

    fun refreshSteps() = viewModelScope.launch {
        _steps.value = runCatching { dot.steps.status() }.getOrNull()
        // Foreground read: works without the background-read permission.
        StepsSyncWorker.syncSteps(dot, days = 35)
    }

    fun hold(habitId: Long) = viewModelScope.launch {
        when (val r = repository.hold(habitId)) {
            is HoldResult.Logged -> _events.emit(UiEvent.Logged(r.entryId, r.habitName))
            is HoldResult.TimerStarted -> _events.emit(UiEvent.Message("${r.habitName}: timer started"))
            is HoldResult.TimerPaused -> _events.emit(UiEvent.Message("${r.habitName}: timer paused"))
            HoldResult.AlreadyDone -> _events.emit(UiEvent.Message("Already complete — open the habit to edit history"))
            HoldResult.Automatic -> _events.emit(UiEvent.Message("Steps update automatically from Health Connect"))
        }
    }

    fun undo(entryId: Long) = viewModelScope.launch { repository.undoEntry(entryId) }

    fun toggleTimer(habitId: Long) = viewModelScope.launch {
        repository.toggleTimer(habitId)
    }

    fun addExamples() = viewModelScope.launch { repository.addExampleHabits() }

    fun viewModelScopeLaunch(block: suspend () -> Unit) = viewModelScope.launch { block() }
}
