/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.core.tag

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Round-trip write tests against generated fixtures.
 *
 * Every case copies the fixture to a temp file first, so the committed fixtures stay
 * pristine, and asserts by reading the file back through the engine.
 */
class TagLibWriteTest {

    private val engine = TagLibEngine()

    @Before
    fun requireNativeLibrary() {
        assumeTrue("MUSICTAG_HOST_LIB not set", NativeLibrary.isAvailable())
    }

    private fun tempCopy(name: String, suffix: String): File {
        val url = javaClass.getResource("/fixtures/$name")
        assertTrue("fixture missing: $name", url != null)
        val temp = File.createTempFile("musictag-write-", suffix)
        File(url!!.toURI()).copyTo(temp, overwrite = true)
        temp.deleteOnExit()
        return temp
    }

    private fun coverFixture(): ByteArray {
        val url = javaClass.getResource("/fixtures/cover.png")
        assertTrue("cover fixture missing", url != null)
        return File(url!!.toURI()).readBytes()
    }

    private fun edited(current: TagData): TagEdit = TagEdit.from(current).copy(
        title = "Новый заголовок 🎵",
        artist = "New Artist",
        album = "New Album",
        albumArtist = "New Album Artist",
        genre = "Ambient",
        composer = "New Composer",
        track = "7/9",
        disc = "2/2",
        year = "2024",
        comment = "written by the test",
        lyrics = "[00:10.00]new lyric line",
    )

    private fun assertEdited(tag: TagData) {
        assertEquals("Новый заголовок 🎵", tag.title)
        assertEquals("New Artist", tag.artist)
        assertEquals("New Album", tag.album)
        assertEquals("New Album Artist", tag.albumArtist)
        assertEquals("Ambient", tag.genre)
        assertEquals("New Composer", tag.composer)
        assertEquals(7, tag.track)
        assertEquals("7/9", tag.trackStr)
        assertEquals(2, tag.disc)
        assertEquals("2/2", tag.discStr)
        assertEquals("2024", tag.year)
        assertEquals("written by the test", tag.comment)
        assertTrue("lyrics: ${tag.lyrics}", tag.lyrics.contains("new lyric line"))
        // Audio must survive a tag rewrite.
        assertEquals(44100, tag.audio.sampleRateHz)
        assertEquals(2, tag.audio.channels)
    }

    @Test
    fun writesAndReadsBackMp3() {
        val file = tempCopy("sample.mp3", ".mp3")
        val before = engine.read(file.absolutePath)!!
        assertTrue("expected ID3v2.3 before the edit, got ${before.tagFormat}",
            before.tagFormat.startsWith("ID3v2.3"))

        val result = engine.write(file.absolutePath, edited(before))
        assertTrue("write failed: $result", result.ok)

        val after = engine.read(file.absolutePath)!!
        assertEdited(after)
        // Default write upgrades to ID3v2.4, as TagLib rewrites the header version.
        assertTrue("expected ID3v2.4, got ${after.summary()}", after.tagFormat.startsWith("ID3v2.4"))
    }

    @Test
    fun honoursId3v23Option() {
        val file = tempCopy("sample.mp3", ".mp3")
        val before = engine.read(file.absolutePath)!!
        val edit = edited(before).copy(id3v23 = true)
        assertTrue(engine.write(file.absolutePath, edit).ok)
        val after = engine.read(file.absolutePath)!!
        assertTrue("expected ID3v2.3, got ${after.summary()}", after.tagFormat.startsWith("ID3v2.3"))
        assertEdited(after)
    }

    @Test
    fun writesId3v1WhenAsked() {
        val file = tempCopy("sample.mp3", ".mp3")
        val before = engine.read(file.absolutePath)!!
        assertTrue(engine.write(file.absolutePath, edited(before).copy(writeId3v1 = true)).ok)
        val after = engine.read(file.absolutePath)!!
        assertTrue("expected an ID3v1 companion tag, got ${after.summary()}",
            after.tagFormat.contains("ID3v1"))
    }

    @Test
    fun writesAndReadsBackFlac() {
        val file = tempCopy("sample.flac", ".flac")
        val before = engine.read(file.absolutePath)!!
        assertEquals("VorbisComment", before.tagFormat)

        assertTrue(engine.write(file.absolutePath, edited(before)).ok)

        val after = engine.read(file.absolutePath)!!
        assertEdited(after)
        assertEquals("VorbisComment", after.tagFormat)
        assertEquals("Fixture cover should survive an unrelated edit", 1, after.pictures.size)
    }

    @Test
    fun replacesCover() {
        val file = tempCopy("sample.flac", ".flac")
        val before = engine.read(file.absolutePath)!!
        val cover = coverFixture()
        val edit = edited(before).copy(cover = CoverEdit.Replace(cover, "image/png"))
        assertTrue(engine.write(file.absolutePath, edit).ok)

        val after = engine.read(file.absolutePath)!!
        assertEquals(1, after.pictures.size)
        assertEquals("image/png", after.pictures[0].mimeType)
        assertArrayEquals(cover, after.pictures[0].data)
    }

    @Test
    fun replacesCoverOnMp3() {
        val file = tempCopy("sample.mp3", ".mp3")
        val before = engine.read(file.absolutePath)!!
        val cover = coverFixture()
        assertTrue(engine.write(file.absolutePath, edited(before)
            .copy(cover = CoverEdit.Replace(cover, "image/png"))).ok)

        val after = engine.read(file.absolutePath)!!
        assertEquals(1, after.pictures.size)
        assertArrayEquals(cover, after.pictures[0].data)
    }

    @Test
    fun removesCover() {
        val list = listOf("sample.flac" to ".flac", "sample.mp3" to ".mp3")
        for ((name, suffix) in list) {
            val file = tempCopy(name, suffix)
            val before = engine.read(file.absolutePath)!!
            assertTrue("$name should start with a cover", before.pictures.isNotEmpty())
            assertTrue(engine.write(file.absolutePath, edited(before).copy(cover = CoverEdit.Remove)).ok)
            val after = engine.read(file.absolutePath)!!
            assertTrue("$name cover should be gone", after.pictures.isEmpty())
        }
    }

    @Test
    fun leavesCoverAloneWhenUnchanged() {
        val file = tempCopy("sample.mp3", ".mp3")
        val before = engine.read(file.absolutePath)!!
        val original = before.pictures.single().data
        assertTrue(engine.write(file.absolutePath, TagEdit.from(before).copy(title = "only the title")).ok)
        val after = engine.read(file.absolutePath)!!
        assertArrayEquals(original, after.pictures.single().data)
    }

    @Test
    fun reportsFailureForNonAudioFile() {
        val text = File.createTempFile("musictag-write-", ".txt").apply {
            writeText("not audio")
            deleteOnExit()
        }
        val result = engine.write(text.absolutePath, TagEdit(title = "x"))
        assertTrue("expected a failure, got $result", result is WriteResult.Failure)
    }

    /**
     * Diagnostic: writes the same field repeatedly and prints what comes back, so an
     * append-vs-replace defect in TagLib's property writer is visible in one CI log.
     */
    @Test
    fun writeDiagnostics() {
        val file = tempCopy("sample.mp3", ".mp3")
        val before = engine.read(file.absolutePath)!!
        println("DIAG before    title=[" + before.title + "] comment=[" + before.comment + "]")
        engine.write(file.absolutePath, TagEdit.from(before).copy(title = "ONE"))
        val one = engine.read(file.absolutePath)!!
        println("DIAG after ONE title=[" + one.title + "] comment=[" + one.comment + "]")
        engine.write(file.absolutePath, TagEdit.from(one).copy(title = "TWO"))
        val two = engine.read(file.absolutePath)!!
        println("DIAG after TWO title=[" + two.title + "] comment=[" + two.comment + "]")
        engine.write(file.absolutePath, TagEdit.from(two).copy(title = "", comment = ""))
        val cleared = engine.read(file.absolutePath)!!
        println("DIAG cleared   title=[" + cleared.title + "] comment=[" + cleared.comment + "]")
    }

    @Test
    fun writesAndReadsBackOgg() {
        val file = tempCopy("sample.ogg", ".ogg")
        val before = engine.read(file.absolutePath)
        assertNotNull("synthetic ogg fixture should be readable", before)
        before!!
        assertEquals("OggVorbis", before.container)
        assertEquals("VorbisComment", before.tagFormat)
        assertEquals("Fixture Title", before.title)

        val result = engine.write(file.absolutePath, edited(before))
        assertTrue("write failed: $result", result.ok)

        val after = engine.read(file.absolutePath)!!
        assertEdited(after)
        assertEquals("OggVorbis", after.container)
    }

    @Test
    fun writesCoverToOgg() {
        val file = tempCopy("sample.ogg", ".ogg")
        val before = engine.read(file.absolutePath)!!
        val cover = coverFixture()
        assertTrue(engine.write(file.absolutePath, edited(before)
            .copy(cover = CoverEdit.Replace(cover, "image/png"))).ok)

        val after = engine.read(file.absolutePath)!!
        assertEquals("one embedded picture expected", 1, after.pictures.size)
        assertArrayEquals(cover, after.pictures[0].data)
    }
}
