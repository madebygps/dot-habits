package com.madebygps.dothabits.widget

import org.junit.Assert.assertTrue
import org.junit.Test

class SingleLayoutTest {
    @Test fun ringFitsEveryWidgetSize() {
        val l = SingleLayout.forSize(80f, 90f)
        assertTrue(l.ringDp <= 64f)
        assertTrue(SingleLayout.forSize(370f, 90f).ringDp <= 74f)
        assertTrue(SingleLayout.forSize(180f, 190f).ringDp <= 164f)
        assertTrue(SingleLayout.forSize(80f, 190f).ringDp <= 64f)
    }
}
