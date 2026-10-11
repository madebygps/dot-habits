package com.madebygps.dothabits.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotIconsTest {
    @Test fun flameHasValidDotMatrixArtworkDistinctFromFallback() {
        val flame = DotIcons.bits("flame")
        assertTrue(DotIcons.validate().isEmpty())
        assertEquals(DotIcons.SIZE * DotIcons.SIZE, flame.size)
        assertTrue(flame.any { it })
        assertFalse(flame.contentEquals(DotIcons.bits("dot")))
        assertTrue(flame[5])
        assertTrue(flame[10 * DotIcons.SIZE + 5])
    }
}
