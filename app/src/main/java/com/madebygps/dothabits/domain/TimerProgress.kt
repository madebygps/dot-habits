package com.madebygps.dothabits.domain

/** Progress is for the current session, not the habit's whole daily goal. */
data class TimerProgress(val elapsed: Int, val remainingSeconds: Long) {
    companion object {
        const val MAX = 1_000

        fun from(
            today: HabitToday,
            runLimitSeconds: Long = today.tileSessionProgress?.totalSeconds ?: today.habit.sessionSeconds,
            runRemainingSeconds: Long = today.sessionRemaining,
        ): TimerProgress? {
            if (!today.timerRunning) return null
            // A duration edit must not make progress wrap before the stored run actually ends.
            val total = maxOf(runLimitSeconds, 1)
            val remaining = runRemainingSeconds.coerceIn(0, total)
            val elapsed = ((total - remaining).toDouble() / total * MAX).toInt().coerceIn(0, MAX)
            return TimerProgress(elapsed, remaining)
        }
    }
}
