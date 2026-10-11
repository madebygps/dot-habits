package com.madebygps.dothabits.domain

/** Progress is for the current session, not the habit's whole daily goal. */
data class TimerProgress(val elapsedSeconds: Int, val totalSeconds: Int, val remainingSeconds: Long) {
    companion object {
        fun from(
            today: HabitToday,
            runLimitSeconds: Long = today.tileSessionProgress?.totalSeconds ?: today.habit.sessionSeconds,
            runRemainingSeconds: Long = today.sessionRemaining,
        ): TimerProgress? {
            if (!today.timerRunning) return null
            // A duration edit must not make progress wrap before the stored run actually ends.
            val total = Math.toIntExact(maxOf(runLimitSeconds, 1))
            val remaining = runRemainingSeconds.coerceIn(0, total.toLong())
            val elapsed = total - remaining.toInt()
            return TimerProgress(elapsed, total, remaining)
        }
    }
}
