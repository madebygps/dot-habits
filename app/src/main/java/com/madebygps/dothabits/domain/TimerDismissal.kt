package com.madebygps.dothabits.domain

/**
 * Dismissal hides a planned session across pause/resume, but only a callback for the generation
 * currently showing may record it, so a late old callback can't hide a resumed newer notification.
 */
object TimerDismissal {
    data class Run(val sessionId: Long, val generation: Long)

    /** The pair to persist after a dismissal callback: only the currently running pair may be dismissed. */
    fun accept(current: Run?, incoming: Run, stored: Run?): Run? = if (incoming == current) incoming else stored

    fun hidden(stored: Run?, current: Run?): Boolean =
        current != null && stored != null && stored.sessionId == current.sessionId && stored.generation <= current.generation
}
