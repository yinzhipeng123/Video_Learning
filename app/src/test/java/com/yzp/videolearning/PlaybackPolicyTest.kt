package com.yzp.videolearning

import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    @Test fun acceptsBothProviderHostsAndKeepsQueryUnrestricted() {
        assertTrue(PlaybackPolicy.allows("https://e54144713.at.baijiayun.com/web/video/player?vid=1&token=abc"))
        assertTrue(PlaybackPolicy.allows("HTTPS://E54144713.AT.BAIJIACLOUD.COM:443/web/video/player?vid=2"))
    }

    @Test fun rejectsLookalikesUnsafeSchemesAndUnexpectedPorts() {
        listOf(null, "", "not a url", "http://e54144713.at.baijiayun.com", "javascript:alert(1)",
            "https://e54144713.at.baijiayun.com.evil.test/", "https://evil.test/?host=e54144713.at.baijiayun.com",
            "https://user@e54144713.at.baijiayun.com", "https://e54144713.at.baijiayun.com:8443/",
            "https://other.at.baijiayun.com/", "file:///android_asset/index.html"
        ).forEach { assertFalse("Unexpectedly allowed $it", PlaybackPolicy.allows(it)) }
    }

    @Test fun readsNumericJavascriptPositionAndPreservesUnavailableFallback() {
        assertEquals(3250L, PlaybackPolicy.positionFromJavascript("3.25"))
        assertEquals(0L, PlaybackPolicy.positionFromJavascript("0"))
        listOf(null, "null", "undefined", "", "{}", "\"3.25\"", "NaN", "-1").forEach {
            assertNull("Unavailable position must not overwrite existing progress", PlaybackPolicy.positionFromJavascript(it))
        }
    }

    @Test fun acceptsOnlyFiniteNonNegativePlaybackPositions() {
        assertEquals(12345L, PlaybackPolicy.positionMillis(12.345))
        assertEquals(0L, PlaybackPolicy.positionMillis(0.0))
        assertNull(PlaybackPolicy.positionMillis(-1.0))
        assertNull(PlaybackPolicy.positionMillis(Double.NaN))
        assertNull(PlaybackPolicy.positionMillis(Double.POSITIVE_INFINITY))
    }
}
