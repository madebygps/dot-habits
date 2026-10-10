package com.madebygps.dothabits.domain

import com.madebygps.dothabits.domain.TimerDismissal.Run
import org.junit.Assert.*
import org.junit.Test

class TimerDismissalTest {
    @Test fun currentGenerationDismissalIsRecordedAndSurvivesResume() {
        val stored = TimerDismissal.accept(Run(7, 1), Run(7, 1), null)
        assertEquals(Run(7, 1), stored)
        assertTrue(TimerDismissal.hidden(stored, Run(7, 3)))
    }

    @Test fun staleGenerationCannotHideResumedNotification() {
        assertNull(TimerDismissal.accept(Run(7, 3), Run(7, 1), null))
        assertFalse(TimerDismissal.hidden(null, Run(7, 3)))
    }

    @Test fun staleCallbackKeepsExistingDismissal() {
        assertEquals(Run(7, 3), TimerDismissal.accept(Run(7, 3), Run(7, 1), Run(7, 3)))
    }

    @Test fun dismissalNeverHidesAnotherSession() {
        assertFalse(TimerDismissal.hidden(Run(7, 3), Run(8, 1)))
        assertNull(TimerDismissal.accept(null, Run(7, 1), null))
    }
}
