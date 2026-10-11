package com.madebygps.dothabits.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveProgressSetupTest {
    @Test fun explainsOnlyForExplicitSetupWithLiveUpdatesAllowed() {
        assertTrue(LiveProgressSetup.shouldExplainBattery(true, true, true, false, false))
        assertFalse(LiveProgressSetup.shouldExplainBattery(false, true, true, false, false))
        assertFalse(LiveProgressSetup.shouldExplainBattery(true, false, true, false, false))
        assertFalse(LiveProgressSetup.shouldExplainBattery(true, true, false, false, false))
    }

    @Test fun doesNotRepeatOrPromptExemptUsers() {
        assertFalse(LiveProgressSetup.shouldExplainBattery(true, true, true, true, false))
        assertFalse(LiveProgressSetup.shouldExplainBattery(true, true, true, false, true))
    }
}
