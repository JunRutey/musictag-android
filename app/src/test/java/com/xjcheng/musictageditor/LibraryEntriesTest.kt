/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import com.xjcheng.musictageditor.media.MediaTrack
import com.xjcheng.musictageditor.ui.library.LibraryEntry
import com.xjcheng.musictageditor.ui.library.LibrarySort
import com.xjcheng.musictageditor.ui.library.buildEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryEntriesTest {

    private fun track(
        id: Long,
        title: String,
        artist: String = "周杰伦",
        album: String = "叶惠美",
        durationMs: Long = 200_000,
    ) = MediaTrack(
        id = id,
        uri = "content://media/external/audio/media/$id",
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        path = "Music/",
        mimeType = "audio/flac",
        displayName = "$title.flac",
    )

    private fun List<LibraryEntry>.letters(): List<String> =
        filterIsInstance<LibraryEntry.Header>().map { it.letter }

    private fun List<LibraryEntry>.titles(): List<String> =
        filterIsInstance<LibraryEntry.Song>().map { it.track.title }

    @Test
    fun sortsByTitleAndEmitsOneHeaderPerInitial() {
        val entries = buildEntries(
            listOf(track(1, "晴天"), track(2, "Always Online"), track(3, "123")),
            LibrarySort.TITLE,
            ascending = true,
        )
        assertEquals(listOf("A", "Q", "#"), entries.letters())
        assertEquals(listOf("Always Online", "晴天", "123"), entries.titles())
        // Every header is immediately followed by its first song.
        assertTrue(entries[0] is LibraryEntry.Header && entries[1] is LibraryEntry.Song)
    }

    @Test
    fun descendingOrderReversesTheSections() {
        val entries = buildEntries(
            listOf(track(1, "晴天"), track(2, "Always Online")),
            LibrarySort.TITLE,
            ascending = false,
        )
        assertEquals(listOf("Q", "A"), entries.letters())
    }

    @Test
    fun sortByArtistGroupsByArtistInitial() {
        val entries = buildEntries(
            listOf(
                track(1, "晴天", artist = "周杰伦"),
                track(2, "富士山下", artist = "陈奕迅"),
                track(3, "十年", artist = "陈奕迅"),
            ),
            LibrarySort.ARTIST,
            ascending = true,
        )
        assertEquals(listOf("C", "Z"), entries.letters())
        assertEquals(listOf("富士山下", "十年", "晴天"), entries.titles())
    }

    @Test
    fun sortByDurationIgnoresInitials() {
        val entries = buildEntries(
            listOf(track(1, "a", durationMs = 300_000), track(2, "b", durationMs = 100_000)),
            LibrarySort.DURATION,
            ascending = true,
        )
        assertEquals(listOf("b", "a"), entries.titles())
    }

    @Test
    fun emptyInputProducesNoEntries() {
        assertTrue(buildEntries(emptyList(), LibrarySort.TITLE, ascending = true).isEmpty())
    }
}
