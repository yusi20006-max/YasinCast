package com.yasin.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompletionRulesTest {
    @Test
    fun completionRequiresAtLeastThirtySecondsAndNinetyPercent() {
        assertFalse(PlaybackCompletionRules.isCompleted(29_000L, 30_000L))
        assertFalse(PlaybackCompletionRules.isCompleted(26_000L, 30_000L))
        assertTrue(PlaybackCompletionRules.isCompleted(27_000L, 30_000L))
        assertTrue(PlaybackCompletionRules.isCompleted(90_000L, 100_000L))
    }

    @Test
    fun resumeKeepsSavedPositionButStartsOverAtEnd() {
        assertEquals(0L, PlaybackCompletionRules.resumePosition(0L, 100_000L))
        assertEquals(42_000L, PlaybackCompletionRules.resumePosition(42_000L, 100_000L))
        assertEquals(0L, PlaybackCompletionRules.resumePosition(100_000L, 100_000L))
        assertEquals(42_000L, PlaybackCompletionRules.resumePosition(42_000L, null))
    }
}
