package com.madebygps.dothabits.widget

import com.madebygps.dothabits.widget.SingleLayout.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleLayoutTest {
    @Test fun oneByOneIsRingOnly() {
        val l = SingleLayout.forSize(80f, 90f)
        assertEquals(Kind.RING, l.kind)
        assertTrue(l.ringDp <= 64f)
    }

    @Test fun wideShortSizesPutTextBesideTheRing() {
        assertEquals(Kind.WIDE, SingleLayout.forSize(180f, 90f).kind)
        assertEquals(Kind.WIDE, SingleLayout.forSize(370f, 90f).kind)
        assertTrue(SingleLayout.forSize(370f, 90f).ringDp <= 74f)
    }

    @Test fun squareAndTallSizesPutTextBelow() {
        val l = SingleLayout.forSize(180f, 190f)
        assertEquals(Kind.TALL, l.kind)
        assertTrue(l.ringDp + 30f <= 190f - 16f)
    }

    @Test fun narrowTallStaysRingOnly() {
        assertEquals(Kind.RING, SingleLayout.forSize(80f, 190f).kind)
    }
}
