package com.madebygps.dothabits.domain

/** App/widget presentation only; the Glyph toys retain their existing progress treatment. */
data class HabitTileState(
    val solid: Boolean = false,
    val interiorFraction: Float = 0f,
    val borderFraction: Float = 0f,
    val segments: Int = 0,
    val dashed: Boolean = false,
    val dimmed: Boolean = false,
) {
    companion object {
        fun from(t: HabitToday): HabitTileState {
            val h = t.habit
            if (h.isNegative) return HabitTileState(dashed = true, dimmed = t.status == TodayStatus.SLIPPED)
            if (h.type == HabitType.STEPS && !t.hasData) return HabitTileState(dimmed = true)
            if (t.status == TodayStatus.REST && !t.timerRunning && !t.timerPaused) return HabitTileState(dimmed = true)
            if (t.status == TodayStatus.DONE) return HabitTileState(solid = true)
            return when (h.type) {
                HabitType.COUNT -> {
                    val goal = if (h.schedule.kind == ScheduleKind.TIMES_PER_WEEK) h.schedule.perWeek else h.dailyTarget
                    HabitTileState(
                        borderFraction = (t.value.toDouble() / goal.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f),
                        segments = h.ringSegments,
                    )
                }
                HabitType.TIMED -> {
                    val done = TimerMath.sessionsDone(t.value.coerceAtLeast(0), h.sessionSeconds, h.sessions)
                    val interior = t.tileSessionProgress?.fraction ?: 0f
                    HabitTileState(
                        borderFraction = (done.toFloat() / h.sessions.coerceAtLeast(1)).coerceIn(0f, 1f),
                        interiorFraction = interior.coerceIn(0f, 1f),
                        segments = h.ringSegments,
                    )
                }
                HabitType.STEPS -> HabitTileState(interiorFraction = t.fraction.coerceIn(0f, 1f))
            }
        }
    }
}

/** Current-session progress below the daily goal; unfinished time never carries into tomorrow. */
data class TileSessionProgress(val totalSeconds: Long, val remainingSeconds: Long) {
    val fraction: Float get() = ((totalSeconds - remainingSeconds).toDouble() / totalSeconds.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
}
