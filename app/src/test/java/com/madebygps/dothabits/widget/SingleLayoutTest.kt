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
        assertTrue(layout.ringDp <= 64f)
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
    }

    @Test fun largeWidgetShowsAllSupportingText() {
        val layout = SingleLayout.forSize(300f, 300f)
        assertEquals(SingleWidgetPresentation.LARGE, layout.presentation)
        assertTrue(layout.showDetail)
        assertTrue(layout.showCaption)
    }

    @Test fun narrowTallWidgetRemainsCompact() {
        val layout = SingleLayout.forSize(80f, 190f)
        assertEquals(SingleWidgetPresentation.COMPACT, layout.presentation)
        assertTrue(layout.ringDp <= 64f)
    }
}
