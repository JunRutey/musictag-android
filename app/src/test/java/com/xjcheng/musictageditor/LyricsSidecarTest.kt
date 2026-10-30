/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import com.xjcheng.musictageditor.io.LyricsSidecar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The naming rules of the `.lrc` side file, which are pure and need no device. */
class LyricsSidecarTest {

    @Test
    fun sameModeKeepsTheAudioName() {
        assertEquals(
            "晴天.lrc",
            LyricsSidecar.fileNameFor("晴天.flac", "same", "", "晴天", "周杰伦", "叶惠美"),
        )
        assertEquals(
            "sample.lrc",
            LyricsSidecar.fileNameFor("sample.mp3", "SAME", "%a - %t", "t", "a", "l"),
        )
    }

    @Test
    fun artistTitleModeJoinsThePartsItHas() {
        assertEquals(
            "周杰伦 - 晴天.lrc",
            LyricsSidecar.fileNameFor("x.flac", "artist-title", "", "晴天", "周杰伦", "叶惠美"),
        )
        assertEquals(
            "晴天.lrc",
            LyricsSidecar.fileNameFor("x.flac", "artist-title", "", "晴天", "", ""),
        )
    }

    @Test
    fun customTemplateExpandsAndIsSanitised() {
        // A path separator in a tag value must not escape the directory.
        assertEquals(
            "01 _ 晴天.lrc",
            LyricsSidecar.fileNameFor("x.flac", "custom", "%t", "01 / 晴天", "周杰伦", "叶惠美"),
        )
        assertEquals(
            "周杰伦 - 晴天 - 叶惠美.lrc",
            LyricsSidecar.fileNameFor("x.flac", "custom", "%a - %t - %l", "晴天", "周杰伦", "叶惠美"),
        )
    }

    @Test
    fun unknownModeFallsBackToTheAudioName() {
        assertEquals("track.lrc", LyricsSidecar.fileNameFor("track.flac", "whatever", "", "t", "a", "l"))
    }

    @Test
    fun blankNameStillProducesAFile() {
        val name = LyricsSidecar.fileNameFor("track.flac", "custom", "%t", "   ", "", "")
        assertTrue(name.endsWith(".lrc"))
        assertEquals("track.lrc", name)
    }
}
