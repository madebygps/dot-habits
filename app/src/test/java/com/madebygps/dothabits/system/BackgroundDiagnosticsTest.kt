package com.madebygps.dothabits.system

import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundDiagnosticsTest {
    @Test
    fun diagnosticLineIncludesWorkerOutcomeAndPhaseTimings() {
        val line = BackgroundSyncDiagnostic(
            startedAtMs = 0,
            workId = "worker",
            runAttempt = 2,
            completion = "success",
            outcome = StepsSyncOutcome.UNCHANGED,
            daysRead = 2,
            cacheChanged = false,
            hadRunningTimer = true,
            timerLifecycleMs = 3,
            stepSyncMs = 5,
            refreshRequestMs = 7,
            totalMs = 11,
        ).asLine()

        assertTrue(line.contains("workId=worker"))
        assertTrue(line.contains("attempt=2"))
        assertTrue(line.contains("outcome=UNCHANGED"))
        assertTrue(line.contains("stepSyncMs=5"))
        assertTrue(line.contains("totalMs=11"))
    }
}
