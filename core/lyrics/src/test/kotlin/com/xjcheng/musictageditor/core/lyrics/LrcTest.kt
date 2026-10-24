/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LrcTest {

    private val sample = """
        [ti:晴天]
        [ar:周杰伦]
        [offset:+200]
        [00:00.00] 晴天
        [00:05.20] 故事的小黄花
        [00:09.40] 从出生那年就飘着
    """.trimIndent() + "\n"

    @Test
    fun parsesTimestampsAndText() {
        val lines = Lrc.parse(sample)
        assertEquals(3, lines.size)
        assertEquals(LrcLine(0, " 晴天"), lines[0])
        assertEquals(LrcLine(5_200, " 故事的小黄花"), lines[1])
        assertEquals(LrcLine(9_400, " 从出生那年就飘着"), lines[2])
    }

    @Test
    fun expandsRepeatedTimestamps() {
        val lines = Lrc.parse("[00:10.00][00:30.50]chorus")
        assertEquals(listOf(LrcLine(10_000, "chorus"), LrcLine(30_500, "chorus")), lines)
    }

    @Test
    fun normalisesFractionWidths() {
        assertEquals(100L, Lrc.parse("[00:01.1]x").single().timeMs)
        assertEquals(120L, Lrc.parse("[00:01.12]x").single().timeMs)
        assertEquals(123L, Lrc.parse("[00:01.123]x").single().timeMs)
        assertEquals(1_000L, Lrc.parse("[00:01]x").single().timeMs)
    }

    @Test
    fun formatsSortedLinesWithTwoDecimals() {
        val text = Lrc.format(
            listOf(
                LrcLine(9_400, "c"),
                LrcLine(0, "a"),
                LrcLine(5_200, "b"),
                LrcLine(1_000, ""),
            ),
        )
        assertEquals("[00:00.00]a\n[00:05.20]b\n[00:09.40]c\n", text)
    }

    @Test
    fun timestampHandlesNegativeAndWidths() {
        assertEquals("[00:00.00]", Lrc.timestamp(-5))
        assertEquals("[01:02.50]", Lrc.timestamp(62_500))
        assertEquals("[01:02.5]", Lrc.timestamp(62_500, decimals = 1))
        assertEquals("[01:02.500]", Lrc.timestamp(62_500, decimals = 3))
    }

    @Test
    fun reformatRewritesWidth() {
        val oneDecimal = Lrc.reformat("[00:05.20]a\n[00:09.40]b\n", decimals = 1)
        assertEquals("[00:05.2]a\n[00:09.4]b\n", oneDecimal)
        val three = Lrc.reformat("[00:05.2]a\n", decimals = 3)
        assertEquals("[00:05.200]a\n", three)
    }

    @Test
    fun reformatLeavesPlainTextAlone() {
        assertEquals("just text\n", Lrc.reformat("just text\n"))
        assertEquals("", Lrc.reformat("  \n"))
    }

    @Test
    fun offsetShiftsAndClamps() {
        assertEquals("[00:02.20]a\n", Lrc.offset("[00:00.20]a\n", 2_000))
        assertEquals("[00:00.00]a\n", Lrc.offset("[00:00.20]a\n", -5_000))
        assertEquals("[00:10.70]a\n", Lrc.offset("[00:10.20]a\n", 500))
        // The original fraction width is preserved.
        assertEquals("[00:03.5]a\n", Lrc.offset("[00:01.5]a\n", 2_000))
    }

    @Test
    fun stripTimestampsKeepsText() {
        val stripped = Lrc.stripTimestamps("[00:01.00]line one\n[00:02.00]line two\n")
        assertEquals("line one\nline two\n", stripped)
        assertEquals("", Lrc.stripTimestamps("[00:01.00]\n"))
    }

    @Test
    fun dropBlankLinesRemovesEmpties() {
        assertEquals("a\nb\n", Lrc.dropBlankLines("a\n\n\nb\n"))
        assertEquals("a\n", Lrc.dropBlankLines("\n\na\n\n"))
    }

    @Test
    fun dropHeadTagsRemovesMetadata() {
        val text = Lrc.dropHeadTags(sample)
        assertEquals("[00:00.00] 晴天\n[00:05.20] 故事的小黄花\n[00:09.40] 从出生那年就飘着\n", text)
    }

    @Test
    fun readsDeclaredOffsetAndMetadata() {
        assertEquals(200L, Lrc.declaredOffset(sample))
        assertNull(Lrc.declaredOffset("[00:01.00]no offset"))
        assertEquals("晴天", Lrc.metadata(sample, "ti"))
        assertEquals("周杰伦", Lrc.metadata(sample, "ar"))
        assertNull(Lrc.metadata(sample, "al"))
    }

    @Test
    fun mergesTranslationByTimestamp() {
        val translation = "[00:00.00] Sunny Day\n[00:09.40] Drifting since birth\n"
        val merged = Lrc.mergeTranslation(sample, translation)
        assertEquals(
            "[00:00.00] 晴天 // Sunny Day\n" +
                "[00:05.20] 故事的小黄花\n" +
                "[00:09.40] 从出生那年就飘着 // Drifting since birth\n",
            merged,
        )
    }

    @Test
    fun keepsTranslationOnlyLines() {
        val merged = Lrc.mergeTranslation("[00:01.00]a\n", "[00:01.00]A\n[00:02.00]B\n")
        assertEquals("[00:01.00]a // A\n[00:02.00]B\n", merged)
    }

    @Test
    fun mergeIsANoOpWithoutTranslation() {
        assertEquals("original", Lrc.mergeTranslation("original", ""))
        assertEquals("original", Lrc.mergeTranslation("original", "   "))
    }

    @Test
    fun parsesRealWorldFixture() {
        val fixture = checkNotNull(javaClass.getResource("/lyrics/sample.lrc")).readText()
        val lines = Lrc.parse(fixture)
        assertEquals(4, lines.size)
        assertEquals("[ti:Fixture Song]", fixture.lineSequence().first())
        assertEquals("01:02.50", Lrc.timestamp(lines.last().timeMs))
        val shifted = Lrc.offset(fixture, -1_000)
        assertEquals(4, Lrc.parse(shifted).size)
    }
}
