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
        track: Int = 0,
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
        track = track,
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

    @Test
    fun artistTracksKeepOneArtistOrderedByAlbumThenTrackNumber() {
        val tracks = listOf(
            track(1, title = "叶惠美-2", artist = "周杰伦", album = "叶惠美", track = 2),
            track(2, title = "八度空间-2", artist = "周杰伦", album = "八度空间", track = 2),
            track(3, title = "八度空间-1", artist = "周杰伦", album = "八度空间", track = 1),
            track(4, title = "别的歌手", artist = "林俊杰", album = "曹操", track = 1),
        )

        val page = MediaGrouping.artistTracks(tracks, "周杰伦")

        // 八度空间 (B) before 叶惠美 (Y), track 1 before track 2, and no other artist.
        assertEquals(listOf(3L, 2L, 1L), page.map { it.id })
    }

    @Test
    fun albumTracksNeedTheArtistTooAndFollowTrackNumbers() {
        val tracks = listOf(
            track(1, artist = "周杰伦", album = "叶惠美", track = 3),
            track(2, artist = "周杰伦", album = "叶惠美", track = 1),
            track(3, artist = "周杰伦", album = "叶惠美", track = 2),
            // Same album name, different artist: a separate page, never merged in.
            track(4, artist = "林俊杰", album = "叶惠美", track = 1),
        )

        assertEquals(listOf(2L, 3L, 1L), MediaGrouping.albumTracks(tracks, "叶惠美", "周杰伦").map { it.id })
        assertEquals(listOf(4L), MediaGrouping.albumTracks(tracks, "叶惠美", "林俊杰").map { it.id })
    }

    @Test
    fun blankArtistAndAlbumPagesStillResolve() {
        val tracks = listOf(
            track(1, artist = "", album = "", track = 2),
            track(2, artist = "  ", album = "  ", track = 1),
            track(3, artist = "周杰伦", album = "叶惠美", track = 1),
        )

        // Blank tags are trimmed on both sides, so " " and "" are the same group. Ordering
        // inside a group with no album is not meaningful, so only membership is asserted
        // there; the album page orders by track number, which is stable.
        assertEquals(setOf(1L, 2L), MediaGrouping.artistTracks(tracks, "").map { it.id }.toSet())
        assertEquals(listOf(2L, 1L), MediaGrouping.albumTracks(tracks, "", "").map { it.id })
    }
}
