/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import com.xjcheng.musictageditor.media.MediaGrouping
import com.xjcheng.musictageditor.media.MediaTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaGroupingTest {

    private fun track(
        id: Long,
        title: String = "t$id",
        artist: String = "",
        album: String = "",
        albumId: Long = 0,
    ) = MediaTrack(
        id = id,
        uri = "content://media/external/audio/media/$id",
        title = title,
        artist = artist,
        album = album,
        durationMs = 200_000,
        path = "Music/",
        mimeType = "audio/flac",
        displayName = "$title.flac",
        albumId = albumId,
    )

    @Test
    fun groupsArtistsWithCountsAndPinyinOrder() {
        val artists = MediaGrouping.artists(
            listOf(
                track(1, artist = "周杰伦", albumId = 11),
                track(2, artist = "周杰伦", albumId = 11),
                track(3, artist = "陈奕迅", albumId = 22),
                track(4, artist = "Lin Jun Jie", albumId = 33),
            ),
        )

        assertEquals(listOf("陈奕迅", "Lin Jun Jie", "周杰伦"), artists.map { it.name })
        assertEquals(2, artists.last().trackCount)
        assertEquals(11L, artists.last().albumId)
    }

    @Test
    fun groupsAlbumsByAlbumAndArtist() {
        val albums = MediaGrouping.albums(
            listOf(
                track(1, artist = "周杰伦", album = "叶惠美", albumId = 11),
                track(2, artist = "周杰伦", album = "叶惠美", albumId = 11),
                track(3, artist = "周杰伦", album = "八度空间", albumId = 12),
                track(4, artist = "林俊杰", album = "叶惠美", albumId = 33),
            ),
        )

        // Same album name, different artist: two rows, not one.
        assertEquals(3, albums.size)
        assertEquals(2, albums.single { it.name == "叶惠美" && it.artist == "周杰伦" }.trackCount)
        assertTrue(albums.any { it.name == "叶惠美" && it.artist == "林俊杰" })
    }

    @Test
    fun blankArtistsAndAlbumsStillGroup() {
        val artists = MediaGrouping.artists(listOf(track(1), track(2, artist = "  ")))
        assertEquals(1, artists.size)
        assertEquals("", artists.single().name)
        assertEquals(2, artists.single().trackCount)

        val albums = MediaGrouping.albums(listOf(track(1), track(2, album = "")))
        assertEquals(1, albums.size)
        assertEquals(2, albums.single().trackCount)
        assertEquals(0L, albums.single().albumId)
    }

    @Test
    fun emptyLibraryProducesEmptyTabs() {
        assertTrue(MediaGrouping.artists(emptyList()).isEmpty())
        assertTrue(MediaGrouping.albums(emptyList()).isEmpty())
    }
}
