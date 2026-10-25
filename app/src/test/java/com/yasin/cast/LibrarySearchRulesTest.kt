package com.yasin.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchRulesTest {
    @Test
    fun normalizesPersianAndArabicLetterVariants() {
        assertEquals("کیف", normalizeSearchText("كِيف"))
        assertEquals("یاسین", normalizeSearchText("ياسین"))
        assertEquals("خانه", normalizeSearchText("خانَة"))
    }

    @Test
    fun searchIsCaseInsensitiveAndTrimsWhitespace() {
        assertEquals(normalizeSearchText("  Episode  "), normalizeSearchText("episode"))
        assertTrue(normalizeSearchText("Podcast Episode").contains(normalizeSearchText("episode")))
    }
}
