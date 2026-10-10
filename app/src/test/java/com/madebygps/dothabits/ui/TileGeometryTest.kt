package com.madebygps.dothabits.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileGeometryTest {
    @Test fun progressLightsOnlyWholeSegments() {
        assertTrue(TileGeometry.filledSegments(0f, 3).isEmpty())
        assertTrue(TileGeometry.filledSegments(.25f, 3).isEmpty())
        assertEquals(TileGeometry.segments(3).take(1), TileGeometry.filledSegments(1f / 3, 3))
        assertEquals(TileGeometry.segments(3), TileGeometry.filledSegments(1.7f, 3))
        for (count in 2..14) for (done in 0..count) {
            assertEquals(done, TileGeometry.filledSegments(done.toFloat() / count, count).size)
        }
    }

    @Test fun continuousBorderClampsItsFraction() {
        assertTrue(TileGeometry.filledSegments(-1f, 0).isEmpty())
        assertEquals(listOf(TileGeometry.Segment(0f, .25f)), TileGeometry.filledSegments(.25f, 0))
        assertEquals(listOf(TileGeometry.Segment(0f, 1f)), TileGeometry.filledSegments(2f, 0))
    }
    @Test fun continuousContourHasNoGap() {
        assertEquals(listOf(TileGeometry.Segment(0f, 1f)), TileGeometry.segments(0))
        assertEquals(listOf(TileGeometry.Segment(0f, 1f)), TileGeometry.segments(1))
    }

    @Test fun segmentsAreClockwiseEqualAndSeparated() {
        for (count in 2..14) {
            val segments = TileGeometry.segments(count)
            assertEquals(count, segments.size)
            val length = segments.first().end - segments.first().start
            segments.forEach {
                assertTrue(it.start >= 0)
                assertTrue(it.end <= 1)
                assertTrue(it.end > it.start)
                assertEquals(length, it.end - it.start, .0001f)
            }
            segments.zipWithNext().forEach { (a, b) -> assertTrue(b.start > a.end) }
        }
    }
}
