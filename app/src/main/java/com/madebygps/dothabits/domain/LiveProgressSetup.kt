package com.madebygps.dothabits.domain

object LiveProgressSetup {
    fun shouldExplainBattery(
        setupRequested: Boolean,
        notificationsAllowed: Boolean,
        liveUpdatesAllowed: Boolean,
        batteryExempt: Boolean,
        alreadyShown: Boolean,
    ): Boolean = setupRequested && notificationsAllowed && liveUpdatesAllowed && !batteryExempt && !alreadyShown
}
