package com.madebygps.dothabits.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetGridTest {
    @Test fun squareWidgetUsesAppTwoByThreeGrid() {
        val g = WidgetGrid.forSize(170f, 170f)
        assertEquals(2 to 3, g.cols to g.rows)
        assertFalse(g.labels)
        assertTrue(g.ringDp in 40f..60f)
    }

    @Test fun wideWidgetSwitchesToThreeByTwoThenSingleRow() {
        assertEquals(3 to 2, WidgetGrid.forSize(360f, 170f).let { it.cols to it.rows })
        assertEquals(6 to 1, WidgetGrid.forSize(360f, 90f).let { it.cols to it.rows })
    }

    @Test fun largeWidgetShowsLabels() {
        val g = WidgetGrid.forSize(360f, 360f)
        assertEquals(2 to 3, g.cols to g.rows)
        assertTrue(g.labels)
    }

    @Test fun ringsAlwaysFitTheirCell() {
        for (w in 100..420 step 20) for (h in 60..420 step 20) {
            val g = WidgetGrid.forSize(w.toFloat(), h.toFloat())
            assertTrue(g.ringDp * g.cols <= w - 20 + 0.01f || g.ringDp == 16f)
            assertTrue(g.ringDp * g.rows <= h - 20 + 0.01f || g.ringDp == 16f)
        }
    }
}
