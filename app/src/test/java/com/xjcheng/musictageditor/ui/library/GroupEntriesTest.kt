/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Section headers of the artist and album browsers. */
class GroupEntriesTest {

    private fun row(title: String) = LibraryGroupRow(
        title = title,
        summary = "",
        albumId = 0L,
        open = {},
    )

    private fun letters(entries: List<GroupEntry>) =
        entries.filterIsInstance<GroupEntry.Header>().map { it.letter }

    @Test
    fun insertsOneHeaderPerInitial() {
        val entries = buildGroupEntries(listOf(row("Adele"), row("Air"), row("Bob")))
        assertEquals(listOf("A", "B"), letters(entries))
        assertEquals(5, entries.size)
    }

    @Test
    fun chineseTitlesUseThePinyinInitial() {
        val entries = buildGroupEntries(
            listOf(row("爱情转移"), row("半岛铁盒"), row("Always Online")),
        )
        assertEquals(listOf("A", "B"), letters(entries))
    }

    @Test
    fun unknownInitialsShareTheHashBucket() {
        val entries = buildGroupEntries(listOf(row("123"), row("###"), row("Adele")))
        assertEquals(listOf("#", "A"), letters(entries))
    }

    @Test
    fun emptyInputHasNoEntries() {
        assertTrue(buildGroupEntries(emptyList()).isEmpty())
    }
}
