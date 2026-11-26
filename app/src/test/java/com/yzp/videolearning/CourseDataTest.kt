package com.yzp.videolearning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CourseDataTest {
    @Test
    fun parsesMinuteAndClockDurations() {
        assertEquals(54L * 60L, parseDurationToSeconds("54"))
        assertEquals(48L * 60L + 35L, parseDurationToSeconds("00:48:35"))
        assertNull(parseDurationToSeconds("bad"))
    }

    @Test
    fun formatsDurationsForDisplay() {
        assertEquals("54分钟", formatDuration(54L * 60L))
        assertEquals("48分35秒", formatDuration(48L * 60L + 35L))
    }
}
