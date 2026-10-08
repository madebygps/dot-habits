package com.madebygps.dothabits.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RingGeometryTest {
    @Test fun continuousRing() {
        val a = RingGeometry.arcs(0.25f, 0)
        assertEquals(listOf(-90f to 360f), a.track)
        assertEquals(listOf(-90f to 90f), a.filled)
        assertTrue(RingGeometry.arcs(0f, 0).filled.isEmpty())
    }

    @Test fun twoSegmentsHalfFillsFirstSegmentOnly() {
        val a = RingGeometry.arcs(0.5f, 2)
        assertEquals(2, a.track.size)
        assertEquals(1, a.filled.size)
        assertEquals(a.track[0], a.filled[0])
    }

    @Test fun segmentsClampOverflow() {
        val a = RingGeometry.arcs(1.7f, 3)
        assertEquals(a.track, a.filled)
    }
}
