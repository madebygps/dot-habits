package com.madebygps.dothabits.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class BuildDetailsTest {
    @Test
    fun buildTimeUsesLocalDateAndTwelveHourTime() {
        assertEquals(
            "Oct 9, 2026, 9:30 PM EDT",
            formatBuildTime(
                "2026-10-10T01:30:00.123456789Z",
                "MMM d, yyyy, h:mm a z",
                Locale.US,
                TimeZone.getTimeZone("America/New_York"),
            ),
        )
    }

    @Test
    fun buildTimeUsesLocalizedMonthAndTwentyFourHourTime() {
        assertEquals(
            "10 oct. 2026, 03:30",
            formatBuildTime(
                "2026-10-10T01:30:00Z",
                "d MMM yyyy, HH:mm",
                Locale.FRANCE,
                TimeZone.getTimeZone("Europe/Paris"),
            ),
        )
    }

    @Test
    fun timeZoneOffsetUsesBuildDateRatherThanCurrentDate() {
        assertEquals(
            "Jan 10, 2026, 8:30 AM EST",
            formatBuildTime(
                "2026-01-10T13:30:00Z",
                "MMM d, yyyy, h:mm a z",
                Locale.US,
                TimeZone.getTimeZone("America/New_York"),
            ),
        )
    }
}
