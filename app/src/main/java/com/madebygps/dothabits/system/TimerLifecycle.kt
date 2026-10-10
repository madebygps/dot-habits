package com.madebygps.dothabits.system

import android.os.SystemClock
import com.madebygps.dothabits.DotApp
import com.madebygps.dothabits.data.toDomain
import com.madebygps.dothabits.domain.SessionState
import com.madebygps.dothabits.domain.TimerMath
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The only expiry observer. Sleeps to the next deadline while the process lives; persisted
 * OS alarms cover process death. No heartbeat writes, polling alarms, service or wake lock.
 */
object TimerLifecycle {
    private val wake = MutableStateFlow(0L)
    suspend fun settle(app: DotApp, sessionId: Long? = null, generation: Long? = null) {
        app.repository.maintainTimers(sessionId = sessionId, generation = generation)
        wake.update { it + 1 }
    }

    fun start(app: DotApp) {
        app.appScope.launch {
            combine(app.repository.raw.map { it.sessions }.distinctUntilChanged(), wake) { sessions, _ -> sessions }
                .collectLatest { sessions ->
                withContext(NonCancellable) { app.repository.maintainTimers() }
                val unfinished = sessions.filter { it.state != SessionState.CLOSED.name }
                if (unfinished.isEmpty()) return@collectLatest
                val clock = TimerMath.ClockReading(System.currentTimeMillis(), SystemClock.elapsedRealtime(), app.repository.bootCount())
                val midnight = unfinished.minOf {
                    LocalDate.ofEpochDay(it.epochDay).plusDays(1)
                        .atStartOfDay(app.repository.zone()).toInstant().toEpochMilli()
                }
                val next = unfinished.mapNotNull {
                    TimerMath.timing(it.toDomain(), clock).endsAt?.toEpochMilli()
                }.plus(midnight).min()
                delay((next - clock.wallMs).coerceAtLeast(1))
                withContext(NonCancellable) { app.repository.maintainTimers() }
            }
        }
    }
}
