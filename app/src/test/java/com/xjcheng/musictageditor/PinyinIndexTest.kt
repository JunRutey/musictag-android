/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import com.xjcheng.musictageditor.media.PinyinIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinIndexTest {

    @Test
    fun bucketsChineseTitlesByPinyinInitial() {
        assertEquals("Q", PinyinIndex.initialOf("晴天"))
        assertEquals("C", PinyinIndex.initialOf("陈奕迅"))
        assertEquals("A", PinyinIndex.initialOf("爱在西元前"))
        assertEquals("B", PinyinIndex.initialOf("半岛铁盒"))
        assertEquals("Y", PinyinIndex.initialOf("一路向北"))
        assertEquals("Z", PinyinIndex.initialOf("周杰伦"))
        // Leading spaces are not part of the bucket.
        assertEquals("Q", PinyinIndex.initialOf("  晴天"))
    }

    @Test
    fun bucketsLatinTitlesByTheirOwnLetter() {
        assertEquals("A", PinyinIndex.initialOf("Always Online"))
        assertEquals("B", PinyinIndex.initialOf("bad guy"))
        assertEquals("Z", PinyinIndex.initialOf("zzz"))
    }

    @Test
    fun bucketsEverythingElseUnderHash() {
        assertEquals("#", PinyinIndex.initialOf("123"))
        assertEquals("#", PinyinIndex.initialOf("（live）"))
        assertEquals("#", PinyinIndex.initialOf("!!!"))
        assertEquals("#", PinyinIndex.initialOf(""))
        assertEquals("#", PinyinIndex.initialOf("   "))
    }

    @Test
    fun sortKeysDropToneDigits() {
        assertEquals("qingtian", PinyinIndex.sortKeyOf("晴天"))
        assertEquals("always", PinyinIndex.sortKeyOf("Always"))
        assertTrue(PinyinIndex.sortKeyOf("叶惠美").startsWith("yehui"))
    }

    @Test
    fun compareOrdersBucketsThenKeys() {
        assertTrue(PinyinIndex.compare("Always", "晴天") < 0)
        assertTrue(PinyinIndex.compare("晴天", "123") < 0)
        assertTrue(PinyinIndex.compare("123", "晴天") > 0)
        // Same bucket: alphabetical by pinyin key, so "abc" precedes "爱国" (aiguo).
        assertTrue(PinyinIndex.compare("abc", "爱国") < 0)
        assertEquals(0, PinyinIndex.compare("abc", "ABC"))
    }

    @Test
    fun sectionsCoverTheAlphabetThenHash() {
        assertEquals(27, PinyinIndex.sections.size)
        assertEquals("A", PinyinIndex.sections.first())
        assertEquals("#", PinyinIndex.sections.last())
    }
}
