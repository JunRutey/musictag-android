/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import com.xjcheng.musictageditor.media.MediaFilters
import com.xjcheng.musictageditor.media.MediaTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFiltersTest {

    private fun track(
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long = 200_000,
        path: String = "Music/Album/song.mp3",
        mimeType: String = "audio/mpeg",
    ) = MediaTrack(1, "content://media/1", title, artist, album, durationMs, path, mimeType)

    @Test
    fun acceptsAnOrdinaryTrack() {
        assertTrue(MediaFilters.shouldInclude(track()))
    }

    @Test
    fun minimumDurationIsExclusiveUnderTheLimit() {
        assertFalse(MediaFilters.shouldInclude(track(durationMs = 4_999), minDurationSeconds = 5))
        assertTrue(MediaFilters.shouldInclude(track(durationMs = 5_000), minDurationSeconds = 5))
        assertTrue(MediaFilters.shouldInclude(track(durationMs = 10), minDurationSeconds = 0))
    }

    @Test
    fun ignoredDirectoriesMatchWholeSegments() {
        val ignored = "/storage/emulated/0/Android\n/storage/emulated/0/Download"
        assertTrue(
            MediaFilters.isIgnoredDirectory(
                track(path = "/storage/emulated/0/Android/media/x.mp3"),
                ignored,
            ),
        )
        assertFalse(
            MediaFilters.isIgnoredDirectory(
                track(path = "/storage/emulated/0/Androidx/x.mp3"),
                ignored,
            ),
        )
        assertTrue(
            MediaFilters.isIgnoredDirectory(track(path = "/storage/emulated/0/Download/x.mp3"), ignored),
        )
        assertFalse(MediaFilters.isIgnoredDirectory(track(), ""))
    }

    @Test
    fun semicolonsAndWindowsSeparatorsWork() {
        val ignored = "D:\\Music\\Junk; E:\\Other "
        assertTrue(MediaFilters.isIgnoredDirectory(track(path = "D:/Music/Junk/x.mp3"), ignored))
        assertTrue(MediaFilters.isIgnoredDirectory(track(path = "E:/Other/x.mp3"), ignored))
        assertFalse(MediaFilters.isIgnoredDirectory(track(path = "D:/Music/x.mp3"), ignored))
    }

    @Test
    fun mp4VideoIsRejectedButM4aIsNot() {
        assertTrue(MediaFilters.isVideo(track(path = "Movies/clip.mp4", mimeType = "video/mp4")))
        assertTrue(MediaFilters.isVideo(track(path = "Music/clip.mp4", mimeType = "audio/mp4")))
        assertFalse(MediaFilters.isVideo(track(path = "Music/a.m4a", mimeType = "audio/x-m4a")))
        assertFalse(MediaFilters.isVideo(track(path = "Music/a.mp3", mimeType = "audio/mpeg")))
        assertFalse(
            MediaFilters.shouldInclude(track(path = "Movies/clip.mp4", mimeType = "video/mp4")),
        )
        assertTrue(
            MediaFilters.shouldInclude(
                track(path = "Movies/clip.mp4", mimeType = "video/mp4"),
                ignoreMp4Videos = false,
            ),
        )
    }

    @Test
    fun queryMatchesTitleArtistOrAlbum() {
        val song = track(title = "晴天", artist = "周杰伦", album = "叶惠美")
        assertTrue(MediaFilters.matchesQuery(song, "晴天"))
        assertTrue(MediaFilters.matchesQuery(song, "周杰伦"))
        assertTrue(MediaFilters.matchesQuery(song, "叶惠"))
        assertFalse(MediaFilters.matchesQuery(song, "富士山"))
        assertTrue(MediaFilters.matchesQuery(song, "   "))
        assertTrue(MediaFilters.matchesQuery(track(title = "Sunny"), "sunny"))
        assertTrue(MediaFilters.matchesQuery(track(title = "Hello", artist = "World"), "lo Wo"))
    }

    @Test
    fun applyCombinesEveryRule() {
        val tracks = listOf(
            track(title = "keep", durationMs = 200_000),
            track(title = "too short", durationMs = 1_000),
            track(title = "ignored dir", path = "Junk/song.mp3"),
            track(title = "video", path = "clip.mp4", mimeType = "video/mp4"),
            track(title = "other", artist = "Someone Else"),
        )
        val kept = MediaFilters.apply(
            tracks,
            minDurationSeconds = 30,
            ignoredDirectories = "Junk",
            ignoreMp4Videos = true,
            query = "",
        )
        assertEquals(listOf("keep", "other"), kept.map { it.title })

        val searched = MediaFilters.apply(
            tracks,
            minDurationSeconds = 30,
            ignoredDirectories = "Junk",
            ignoreMp4Videos = true,
            query = "keep",
        )
        assertEquals(listOf("keep"), searched.map { it.title })
    }
}
