package com.madebygps.dothabits

import android.app.Application
import androidx.room.Room
import com.madebygps.dothabits.data.DotDatabase
import com.madebygps.dothabits.data.HabitRepository
import com.madebygps.dothabits.data.SettingsStore
import com.madebygps.dothabits.data.StepsRepository
import com.madebygps.dothabits.domain.TimerMath
import com.madebygps.dothabits.system.Notifications
import com.madebygps.dothabits.system.Refresh
import com.madebygps.dothabits.system.StepsSyncWorker
import com.madebygps.dothabits.system.TimerNotificationUpdater
import com.madebygps.dothabits.system.TimerLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; the app is small enough not to need a DI framework. */
class DotApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: DotDatabase by lazy {
        Room.databaseBuilder(this, DotDatabase::class.java, "dot-habits.db")
            .build()
    }
    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: HabitRepository by lazy {
        HabitRepository(database, settings, contentResolver).also { repo ->
            repo.onDataChanged = { Refresh.afterDataChange(this, reason = "repository-write") }
            repo.onSessionsFinished = { ids ->
                val snap = repo.currentSnapshot()
                ids.distinct().forEach { id ->
                    snap.habits.firstOrNull { it.habit.id == id }?.let { t ->
                        Notifications.sessionFinished(this, t.habit, TimerMath.sessionsDone(t.value, t.habit.sessionSeconds, t.habit.sessions))
                    }
                }
            }
        }
    }
    val steps: StepsRepository by lazy { StepsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        StepsSyncWorker.schedule(this)
        appScope.launch {
            TimerLifecycle.settle(this@DotApp)
            Refresh.afterDataChange(this@DotApp, reason = "process-start")
            TimerLifecycle.start(this@DotApp)
            TimerNotificationUpdater.start(this@DotApp)
        }
    }
}

val android.content.Context.dotApp: DotApp get() = applicationContext as DotApp
