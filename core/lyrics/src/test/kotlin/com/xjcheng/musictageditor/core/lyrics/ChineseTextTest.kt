/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseTextTest {

    @Test
    fun convertsCommonTraditionalWords() {
        assertEquals("汉语", ChineseText.toSimplified("漢語"))
        assertEquals("台湾", ChineseText.toSimplified("臺灣"))
        assertEquals("头发", ChineseText.toSimplified("頭髮"))
        assertEquals("干杯", ChineseText.toSimplified("乾杯"))
        assertEquals("后来", ChineseText.toSimplified("後來"))
    }

    @Test
    fun convertsBackToTraditional() {
        assertEquals("漢語", ChineseText.toTraditional("汉语"))
        assertEquals("臺灣", ChineseText.toTraditional("台湾"))
    }

    @Test
    fun leavesSimplifiedTextAlone() {
        val simplified = "故事的小黄花 从出生那年就飘着"
        assertEquals(simplified, ChineseText.toSimplified(simplified))
        assertFalse(ChineseText.hasTraditional(simplified))
    }

    @Test
    fun detectsTraditionalText() {
        assertTrue(ChineseText.hasTraditional("後來 我們"))
        assertEquals("后来 我们", ChineseText.toSimplified("後來 我們"))
    }

    @Test
    fun handlesEmptyAndAsciiText() {
        assertEquals("", ChineseText.toSimplified(""))
        assertEquals("hello", ChineseText.toSimplified("hello"))
        assertEquals("", ChineseText.toTraditional(""))
    }

    @Test
    fun convertsASentenceInsideLyrics() {
        val traditional = "[00:00.00] 後來\n[00:05.00] 終於明白"
        assertEquals("[00:00.00] 后来\n[00:05.00] 终于明白", ChineseText.toSimplified(traditional))
    }
}
