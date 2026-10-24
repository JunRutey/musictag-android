/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import com.xjcheng.musictageditor.batch.BatchEdit
import com.xjcheng.musictageditor.batch.BatchField
import com.xjcheng.musictageditor.batch.BatchMode
import com.xjcheng.musictageditor.batch.BatchProgress
import com.xjcheng.musictageditor.batch.BatchRunner
import com.xjcheng.musictageditor.batch.BatchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchRunnerTest {

    private val items = listOf(
        BatchTarget("uri://1", "one.mp3", mapOf(BatchField.TITLE to "old")),
        BatchTarget("uri://2", "two.mp3", mapOf(BatchField.TITLE to "")),
        BatchTarget("uri://3", "three.mp3", mapOf(BatchField.TITLE to "three")),
    )

    private fun runner(writes: MutableList<Pair<String, Map<BatchField, String>>>) = BatchRunner(
        readFields = { uri -> items.first { it.uri == uri }.existing },
        writeFields = { uri, values ->
            writes += uri to values
            null
        },
    )

    @Test
    fun writesEachItemAndReportsProgress() {
        val writes = mutableListOf<Pair<String, Map<BatchField, String>>>()
        val progress = mutableListOf<BatchProgress>()
        val results = runner(writes).run(
            items,
            BatchEdit(mapOf(BatchField.ARTIST to "Artist")),
            onProgress = { progress += it },
        )

        assertEquals(3, results.size)
        assertTrue(results.all { it.success })
        assertEquals(3, writes.size)
        // 1 start event plus one per item.
        assertEquals(4, progress.size)
        assertEquals(BatchProgress(0, 3), progress.first())
        assertEquals(3, progress.last().processed)
        assertEquals(100, progress.last().percent)
        assertEquals("one.mp3", progress[1].currentName)
    }

    @Test
    fun skipsItemsThatWouldNotChange() {
        val writes = mutableListOf<Pair<String, Map<BatchField, String>>>()
        // Only item 2 has a blank title, so only it changes under FILL_MISSING.
        val results = runner(writes).run(
            items,
            BatchEdit(mapOf(BatchField.TITLE to "filled"), mode = BatchMode.FILL_MISSING),
        )
        assertEquals(1, writes.size)
        assertEquals("uri://2", writes.single().first)
        assertEquals(2, results.count { it.unchanged })
    }

    @Test
    fun stopsBetweenItemsWhenCancelled() {
        val writes = mutableListOf<Pair<String, Map<BatchField, String>>>()
        var processed = 0
        val results = runner(writes).run(
            items,
            BatchEdit(mapOf(BatchField.ARTIST to "Artist")),
            isCancelled = { processed++ >= 1 },
        )
        assertEquals(1, results.size)
        assertEquals(1, writes.size)
    }

    @Test
    fun recordsWriteFailuresAndContinues() {
        val failing = BatchRunner(
            readFields = { uri -> items.first { it.uri == uri }.existing },
            writeFields = { uri, _ -> if (uri == "uri://2") "read-only" else null },
        )
        val results = failing.run(items, BatchEdit(mapOf(BatchField.ARTIST to "Artist")))

        assertEquals(3, results.size)
        assertFalse(results[1].success)
        assertEquals("read-only", results[1].message)
        assertTrue(results[0].success && results[2].success)
        assertEquals("2 written, 1 failed", failing.summarize(results))
    }

    @Test
    fun recordsThrownErrorsAsFailures() {
        val throwing = BatchRunner(
            readFields = { error("cannot read") },
            writeFields = { _, _ -> null },
        )
        val results = throwing.run(items, BatchEdit(mapOf(BatchField.TITLE to "x")))
        assertEquals(3, results.size)
        assertTrue(results.none { it.success })
        assertEquals("cannot read", results.first().message)
    }

    @Test
    fun summariseCountsUnchangedAndFailed() {
        val runner = runner(mutableListOf())
        val results = runner.run(
            items,
            BatchEdit(mapOf(BatchField.TITLE to "three")),
        )
        // Item 3 already has "three"; items 1 and 2 change.
        assertEquals("2 written, 1 unchanged", runner.summarize(results))
    }

    @Test
    fun emptyJobIsHarmless() {
        val runner = runner(mutableListOf())
        val results = runner.run(emptyList(), BatchEdit(mapOf(BatchField.TITLE to "x")))
        assertTrue(results.isEmpty())
        assertEquals("0 written", runner.summarize(results))
    }
}
