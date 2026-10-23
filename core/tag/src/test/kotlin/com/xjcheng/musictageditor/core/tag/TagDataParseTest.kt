/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.tag

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the flat-array -> model mapping without touching native code, so the JNI
 * contract is verified even on a machine that cannot build TagLib.
 */
class TagDataParseTest {

    private val engine = TagLibEngine()

    private fun array(
        container: String = "FLAC",
        tagFormat: String = "VorbisComment",
        track: String = "3",
        trackStr: String = "3/12",
        pictureCount: String = "1",
        pictures: List<String> = listOf("image/png", "aGVsbG8="),
    ): Array<String> {
        val values = MutableList(21) { "" }
        values[0] = container
        values[1] = tagFormat
        values[2] = "Title"
        values[3] = "Artist"
        values[4] = "Album"
        values[5] = "Album Artist"
        values[6] = "Jazz"
        values[7] = "Composer"
        values[8] = track
        values[9] = trackStr
        values[10] = "1"
        values[11] = "1/2"
        values[12] = "2021"
        values[13] = "Comment"
        values[14] = "[00:01.00]line"
        values[15] = "3000"
        values[16] = "900"
        values[17] = "44100"
        values[18] = "2"
        values[19] = "16"
        values[20] = pictureCount
        return (values + pictures).toTypedArray()
    }

    @Test
    fun mapsEveryField() {
        val tag = engine.parse(array())!!
        assertEquals("FLAC", tag.container)
        assertEquals("VorbisComment", tag.tagFormat)
        assertEquals("Title", tag.title)
        assertEquals("Artist", tag.artist)
        assertEquals("Album", tag.album)
        assertEquals("Album Artist", tag.albumArtist)
        assertEquals("Jazz", tag.genre)
        assertEquals("Composer", tag.composer)
        assertEquals(3, tag.track)
        assertEquals("3/12", tag.trackStr)
        assertEquals(1, tag.disc)
        assertEquals("1/2", tag.discStr)
        assertEquals("2021", tag.year)
        assertEquals("Comment", tag.comment)
        assertEquals("[00:01.00]line", tag.lyrics)
        assertEquals(3000, tag.audio.durationMs)
        assertEquals(900, tag.audio.bitrateKbps)
        assertEquals(44100, tag.audio.sampleRateHz)
        assertEquals(2, tag.audio.channels)
        assertEquals(16, tag.audio.bitsPerSample)
    }

    @Test
    fun decodesPictures() {
        val tag = engine.parse(array())!!
        assertEquals(1, tag.pictures.size)
        assertEquals("image/png", tag.pictures[0].mimeType)
        assertArrayEquals("hello".toByteArray(), tag.pictures[0].data)
    }

    @Test
    fun toleratesMissingPicturesAndEmptyNumbers() {
        val tag = engine.parse(array(pictureCount = "0", pictures = emptyList(), track = "", trackStr = ""))!!
        assertTrue(tag.pictures.isEmpty())
        assertEquals(0, tag.track)
        assertEquals("", tag.trackStr)
    }

    @Test
    fun rejectsTruncatedArray() {
        assertNull(engine.parse(emptyArray()))
        assertNull(engine.parse(Array(20) { "" }))
    }

    @Test
    fun summaryIsHumanReadable() {
        val summary = engine.parse(array())!!.summary()
        assertTrue(summary, summary.startsWith("FLAC / VorbisComment"))
        assertTrue(summary, summary.contains("3s"))
        assertTrue(summary, summary.contains("1 cover(s)"))
    }
}

class Base64CodecTest {

    @Test
    fun decodesPaddedAndUnpaddedInput() {
        assertArrayEquals("hello".toByteArray(), Base64Codec.decode("aGVsbG8="))
        assertArrayEquals("hello".toByteArray(), Base64Codec.decode("aGVsbG8"))
        assertArrayEquals(ByteArray(0), Base64Codec.decode(""))
        assertArrayEquals(byteArrayOf(0x00), Base64Codec.decode("AA=="))
    }

    @Test
    fun ignoresWhitespace() {
        assertArrayEquals("hello".toByteArray(), Base64Codec.decode("aGVs bG8="))
    }
}
