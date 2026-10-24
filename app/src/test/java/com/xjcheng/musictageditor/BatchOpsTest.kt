/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.batch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchOpsTest {

    private val existing = mapOf(
        BatchField.TITLE to "Old title",
        BatchField.ARTIST to "Old artist",
        BatchField.ALBUM to "",
    )

    @Test
    fun overwriteReplacesSuppliedFieldsAndKeepsTheRest() {
        val edit = BatchEdit(mapOf(BatchField.TITLE to "New title"))
        val result = BatchOps.apply(existing, edit)
        assertEquals("New title", result[BatchField.TITLE])
        assertEquals("Old artist", result[BatchField.ARTIST])
        assertFalse(result.containsKey(BatchField.GENRE))
    }

    @Test
    fun emptyValueClearsTheField() {
        val edit = BatchEdit(mapOf(BatchField.ARTIST to ""))
        val result = BatchOps.apply(existing, edit)
        assertFalse(result.containsKey(BatchField.ARTIST))
        assertEquals("Old title", result[BatchField.TITLE])
    }

    @Test
    fun fillMissingOnlyTouchesBlankFields() {
        val edit = BatchEdit(
            mapOf(
                BatchField.TITLE to "New title",
                BatchField.ALBUM to "New album",
            ),
            mode = BatchMode.FILL_MISSING,
        )
        val result = BatchOps.apply(existing, edit)
        assertEquals("Old title", result[BatchField.TITLE])
        assertEquals("New album", result[BatchField.ALBUM])
    }

    @Test
    fun wouldChangeDetectsNoOps() {
        val noop = BatchEdit(emptyMap())
        assertFalse(BatchOps.wouldChange(existing, noop))
        assertTrue(BatchOps.wouldChange(existing, BatchEdit(mapOf(BatchField.TITLE to "New title"))))
        assertFalse(BatchOps.wouldChange(existing, BatchEdit(mapOf(BatchField.TITLE to "Old title"))))
        // Fill-missing on an already filled field changes nothing either.
        assertFalse(
            BatchOps.wouldChange(
                existing,
                BatchEdit(mapOf(BatchField.TITLE to "New title"), mode = BatchMode.FILL_MISSING),
            ),
        )
    }

    @Test
    fun renameExpandsEveryPlaceholder() {
        val values = mapOf(
            BatchField.TITLE to "Song",
            BatchField.ARTIST to "Artist",
            BatchField.ALBUM to "Album",
            BatchField.ALBUM_ARTIST to "AlbumArtist",
            BatchField.TRACK to "3",
            BatchField.YEAR to "2003",
        )
        assertEquals(
            "Artist - Song",
            BatchOps.rename("%a - %t", values, fallback = "file"),
        )
        // The separator is sanitised, so a template cannot create directories.
        assertEquals(
            // Track numbers are expanded literally; only the separator is sanitised.
            "3 Album_Song (2003)",
            BatchOps.rename("%n %l/%t (%y)", values, fallback = "file"),
        )
        assertEquals(
            "AlbumArtist 100%",
            BatchOps.rename("%A 100%%", values, fallback = "file"),
        )
        assertEquals("x%z", BatchOps.rename("x%z", values, fallback = "file"))
    }

    @Test
    fun renameFallsBackWhenTheTemplateExpandsToNothing() {
        assertEquals("original", BatchOps.rename("%t", emptyMap(), fallback = "original"))
        assertEquals("untitled", BatchOps.rename("   ", emptyMap(), fallback = ""))
    }

    @Test
    fun sanitizeKeepsNamesLegal() {
        assertEquals("a_b_c", BatchOps.sanitize("a/b:c"))
        assertEquals("a_b", BatchOps.sanitize("a?b"))
        assertEquals("a b", BatchOps.sanitize("  a   b  "))
        assertEquals("name", BatchOps.sanitize("name..."))
        assertEquals(120, BatchOps.sanitize("x".repeat(500)).length)
        assertEquals("", BatchOps.sanitize("\u0001\u0002"))
        assertEquals("ab", BatchOps.sanitize("a\u0001b"))
    }

    @Test
    fun processLyricsAppliesTheConfiguredStepsInOrder() {
        val lyrics = "[ti:Song]\n[00:01.5] line one\n\n[00:02.500] line two\n"
        val processed = BatchOps.processLyrics(
            lyrics = lyrics,
            reformatTimestamps = true,
            removeTimestamps = false,
            dropBlankLines = true,
            dropHeadTags = true,
        )
        assertEquals("[00:01.50] line one\n[00:02.50] line two\n", processed)
    }

    @Test
    fun processLyricsCanStripAndOffset() {
        val stripped = BatchOps.processLyrics("[00:01.00]a\n", removeTimestamps = true)
        assertEquals("a\n", stripped)

        val offset = BatchOps.processLyrics("[00:01.00]a\n", offsetMs = 1_500)
        assertEquals("[00:02.50]a\n", offset)
    }

    @Test
    fun processLyricsMergesTranslationsFirst() {
        val merged = BatchOps.processLyrics(
            lyrics = "[00:01.00]line\n",
            mergeTranslation = { original -> original.replace("line", "line // translation") },
        )
        assertEquals("[00:01.00]line // translation\n", merged)
    }

    @Test
    fun everyFieldHasAUniquePlaceholder() {
        val placeholders = BatchField.entries.map { it.placeholder }
        assertEquals(placeholders.size, placeholders.toSet().size)
        assertEquals(11, BatchField.entries.size)
    }
}
