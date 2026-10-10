package com.madebygps.dothabits.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleLayoutTest {
    @Test fun compactWidgetShowsOnlyTheRing() {
        val layout = SingleLayout.forSize(80f, 90f)
        assertEquals(SingleWidgetPresentation.COMPACT, layout.presentation)
        assertFalse(layout.showDetail)
        assertFalse(layout.showCaption)
        assertEquals(56f, layout.ringDp, 0.01f)
    }

    @Test fun shortWideWidgetPlacesTextBesideRing() {
        val layout = SingleLayout.forSize(170f, 80f)
        assertEquals(SingleWidgetPresentation.WIDE, layout.presentation)
        assertTrue(layout.showDetail)
        assertFalse(layout.showCaption)
        assertTrue(layout.ringDp <= 64f)
    }

    @Test fun fullWidthStripAlsoShowsSupportingCaption() {
        val layout = SingleLayout.forSize(370f, 90f)
        assertEquals(SingleWidgetPresentation.WIDE, layout.presentation)
        assertTrue(layout.showCaption)
        assertTrue(layout.ringDp <= 74f)
    }

    @Test fun mediumWidgetStacksTextBelowRing() {
        val layout = SingleLayout.forSize(180f, 190f)
        assertEquals(SingleWidgetPresentation.STACKED, layout.presentation)
        assertTrue(layout.showDetail)
        assertTrue(layout.ringDp <= 164f)
        assertEquals(131.4f, layout.ringDp, 0.01f)
    }

    @Test fun largeWidgetShowsAllSupportingText() {
        val layout = SingleLayout.forSize(300f, 300f)
        assertEquals(SingleWidgetPresentation.LARGE, layout.presentation)
        assertTrue(layout.showDetail)
        assertTrue(layout.showCaption)
        assertEquals(214.2f, layout.ringDp, 0.01f)
    }

    @Test fun ringsAndSupportingTextFitAcrossLauncherSizes() {
        for (w in 70..420 step 10) for (h in 50..420 step 10) {
            val layout = SingleLayout.forSize(w.toFloat(), h.toFloat())
            val textDp = 14f + if (layout.showCaption) 14f else 0f
            val contentHeight = when (layout.presentation) {
                SingleWidgetPresentation.COMPACT,
                SingleWidgetPresentation.WIDE,
                -> layout.ringDp
                SingleWidgetPresentation.STACKED -> layout.ringDp + 6f + textDp
                SingleWidgetPresentation.LARGE -> layout.ringDp + 10f + textDp
            }
            assertTrue("w=$w h=$h $layout", layout.ringDp <= w - 24f)
            assertTrue("w=$w h=$h $layout", contentHeight <= h - 24f)
        }
    }

    @Test fun narrowTallWidgetRemainsCompact() {
        val layout = SingleLayout.forSize(80f, 190f)
        assertEquals(SingleWidgetPresentation.COMPACT, layout.presentation)
        assertTrue(layout.ringDp <= 64f)
    }
}
