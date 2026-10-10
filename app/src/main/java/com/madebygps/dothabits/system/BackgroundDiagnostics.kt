package com.madebygps.dothabits.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

data class BackgroundSyncDiagnostic(
    val startedAtMs: Long,
    val workId: String,
    val runAttempt: Int,
    val completion: String,
    val outcome: StepsSyncOutcome,
    val daysRead: Int,
    val cacheChanged: Boolean,
    val hadRunningTimer: Boolean,
    val timerLifecycleMs: Long,
    val stepSyncMs: Long,
    val refreshRequestMs: Long,
    val totalMs: Long,
) {
    fun asLine(): String =
        "at=${Instant.ofEpochMilli(startedAtMs)} workId=$workId attempt=$runAttempt completion=$completion " +
            "outcome=$outcome daysRead=$daysRead cacheChanged=$cacheChanged runningTimer=$hadRunningTimer " +
            "timerLifecycleMs=$timerLifecycleMs stepSyncMs=$stepSyncMs refreshRequestMs=$refreshRequestMs totalMs=$totalMs"
}

object BackgroundDiagnostics {
    const val FILE_NAME = "background-sync.log"
    private const val TAG = "DotHabitsWorker"
    private const val MAX_ENTRIES = 192
    private val fileLock = Any()

    suspend fun record(context: Context, diagnostic: BackgroundSyncDiagnostic) {
        val line = diagnostic.asLine()
        Log.i(TAG, line)
        withContext(Dispatchers.IO) {
            runCatching {
                synchronized(fileLock) {
                    val file = context.filesDir.resolve(FILE_NAME)
                    val retained = if (file.exists()) file.readLines().takeLast(MAX_ENTRIES - 1) else emptyList()
                    file.writeText((retained + line).joinToString(separator = "\n", postfix = "\n"))
                }
            }.onFailure { Log.w(TAG, "failed to persist background diagnostics", it) }
        }
    }
}
