package com.madebygps.dothabits.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotTextLayoutTest {
    @Test fun screenTitlesFitAtDefaultSize() {
        assertTrue(fitsDotText("Settings", 4.dp, 200.dp, 24.dp))
        assertTrue(fitsDotText("Statistics", 4.dp, 200.dp, 24.dp))
    }

    @Test fun exactBoundsFitWithoutShrinking() {
        assertTrue(fitsDotText("Statistics", 4.dp, 156.dp, 20.dp))
        assertFalse(fitsDotText("Statistics", 4.dp, 155.dp, 20.dp))
        assertFalse(fitsDotText("Statistics", 4.dp, 156.dp, 19.dp))
    }

    @Test fun scaledDotsFallBackWhenTheyNoLongerFit() {
        assertTrue(fitsDotText("Statistics", 4.dp, 200.dp, 40.dp))
        assertFalse(fitsDotText("Statistics", 8.dp, 200.dp, 40.dp))
        assertTrue(fitsDotText("Statistics", 8.dp, 312.dp, 40.dp))
    }

    @Test fun unsupportedCharactersUseRegularText() {
        assertFalse(fitsDotText("Caf\u00e9", 4.dp, 200.dp, 40.dp))
        assertFalse(fitsDotText("\u00df", 4.dp, 200.dp, 40.dp))
        assertFalse(fitsDotText("Hello!", 4.dp, 200.dp, 40.dp))
        assertTrue(fitsDotText("MON 12", 4.dp, 200.dp, 40.dp))
        assertTrue(fitsDotText("1:23:45", 4.dp, 200.dp, 40.dp))
    }
}
