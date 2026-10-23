/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Real TagLib reads against generated fixtures.
 *
 * Requires the host build of the JNI library (`MUSICTAG_HOST_LIB`), produced by the
 * native CI job. Without it the tests skip instead of failing, so a plain
 * `./gradlew test` on a machine without cmake/TagLib stays green.
 *
 * Fixtures come from tools/gen_fixtures.py and contain no music: silence plus a tag.
 */
class TagLibReadTest {

    private val engine = TagLibEngine()

    @Before
    fun requireNativeLibrary() {
        assumeTrue(
            "MUSICTAG_HOST_LIB not set (build native/taglib for the host and point at libmusictag_jni.so)",
            NativeLibrary.isAvailable(),
        )
    }

    private fun fixture(name: String): String {
        val url = javaClass.getResource("/fixtures/$name")
        assertNotNull("fixture missing from test resources: $name", url)
        return File(url!!.toURI()).absolutePath
    }

    @Test
    fun reportsUpstreamTaglibVersion() {
        val version = engine.version()
        assertTrue("unexpected TagLib version: $version", version.matches(Regex("""\d+\.\d+\.\d+""")))
        assertTrue("TagLib 2.x expected, got $version", version.startsWith("2."))
    }

    @Test
    fun readsId3v23TagsFromMp3() {
        val tag = engine.read(fixture("sample.mp3"))
        assertNotNull("mp3 fixture should be readable", tag)
        tag!!
        assertEquals("container of ${tag.summary()}", "MPEG", tag.container)
        assertTrue("expected ID3v2.3, got ${tag.summary()}", tag.tagFormat.startsWith("ID3v2.3"))
        assertEquals("Fixture Title", tag.title)
        assertEquals("Fixture Artist", tag.artist)
        assertEquals("Fixture Album", tag.album)
        assertEquals("Fixture Album Artist", tag.albumArtist)
        assertEquals("Jazz", tag.genre)
        assertEquals("Fixture Composer", tag.composer)
        assertEquals(3, tag.track)
        assertEquals("3/12", tag.trackStr)
        assertEquals(1, tag.disc)
        assertEquals("1/2", tag.discStr)
        assertEquals("2021", tag.year)
        assertEquals("Fixture Comment", tag.comment)
        assertTrue("lyrics should be read from USLT, got '${tag.lyrics}'", tag.lyrics.contains("fixture line one"))
    }

    @Test
    fun readsVorbisCommentsFromFlac() {
        val tag = engine.read(fixture("sample.flac"))
        assertNotNull("flac fixture should be readable", tag)
        tag!!
        assertEquals("container of ${tag.summary()}", "FLAC", tag.container)
        assertEquals("tag format of ${tag.summary()}", "VorbisComment", tag.tagFormat)
        assertEquals("Fixture Title", tag.title)
        assertEquals("Fixture Artist", tag.artist)
        assertEquals("Fixture Album", tag.album)
        assertEquals("Fixture Album Artist", tag.albumArtist)
        assertEquals("Jazz", tag.genre)
        assertEquals("Fixture Composer", tag.composer)
        assertEquals(3, tag.track)
        assertEquals("3/12", tag.trackStr)
        assertEquals(1, tag.disc)
        assertEquals("1/2", tag.discStr)
        assertEquals("2021", tag.year)
        assertEquals("Fixture Comment", tag.comment)
        assertTrue(tag.lyrics.contains("fixture line one"))
    }

    @Test
    fun reportsAudioProperties() {
        val flac = engine.read(fixture("sample.flac"))!!
        assertEquals(44100, flac.audio.sampleRateHz)
        assertEquals(2, flac.audio.channels)
        assertEquals(16, flac.audio.bitsPerSample)
        assertTrue("duration should come from STREAMINFO, got ${flac.audio.durationMs}",
            flac.audio.durationMs in 2900..3100)

        val mp3 = engine.read(fixture("sample.mp3"))!!
        assertEquals(44100, mp3.audio.sampleRateHz)
        assertEquals(2, mp3.audio.channels)
        assertTrue("bitrate should be parsed from the MPEG header", mp3.audio.bitrateKbps > 0)
    }

    @Test
    fun readsEmbeddedCover() {
        val flac = engine.read(fixture("sample.flac"))!!
        assertEquals(1, flac.pictures.size)
        assertEquals("image/png", flac.pictures[0].mimeType)
        assertTrue("cover payload should be non-empty", flac.pictures[0].size > 0)
        // PNG signature survives the base64 round trip.
        val head = flac.pictures[0].data.take(8).toByteArray()
        assertEquals(listOf(0x89.toByte(), 0x50, 0x4E, 0x47), head.take(4))

        val mp3 = engine.read(fixture("sample.mp3"))!!
        assertEquals(1, mp3.pictures.size)
        assertEquals("image/png", mp3.pictures[0].mimeType)
    }

    @Test
    fun returnsNullForMissingFile() {
        assertNull(engine.read(File(System.getProperty("java.io.tmpdir"), "musictag-does-not-exist.mp3").absolutePath))
    }

    @Test
    fun returnsNullForNonAudioFile() {
        val textFile = File.createTempFile("musictag-fixture", ".txt").apply {
            writeText("this is not audio")
            deleteOnExit()
        }
        assertNull(engine.read(textFile.absolutePath))
    }
}
