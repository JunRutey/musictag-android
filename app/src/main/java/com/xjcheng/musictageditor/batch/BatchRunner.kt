/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.batch

/** Progress of a running batch, delivered to the notification and the UI. */
data class BatchProgress(
    val processed: Int,
    val total: Int,
    val currentName: String? = null,
) {
    val percent: Int get() = if (total <= 0) 0 else (processed * 100) / total
}

data class BatchItemResult(
    val uri: String,
    val displayName: String,
    val success: Boolean,
    val message: String? = null,
) {
    val unchanged: Boolean get() = message == UNCHANGED

    companion object {
        const val UNCHANGED = "unchanged"
    }
}

/**
 * Runs a [BatchEdit] over a list of files.
 *
 * I/O is injected: [readFields] loads the current values, [writeFields] applies the new
 * ones and returns an error message when it fails (`null` means success). That keeps
 * ordering, progress, cancellation and error aggregation testable without a device, while
 * the foreground service supplies the real tag engine.
 */
class BatchRunner(
    private val readFields: (String) -> Map<BatchField, String>,
    private val writeFields: (String, Map<BatchField, String>) -> String?,
) {

    /**
     * @param isCancelled polled before every item, so cancellation takes effect between
     *   files rather than in the middle of a write
     */
    fun run(
        items: List<BatchTarget>,
        edit: BatchEdit,
        onProgress: (BatchProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): List<BatchItemResult> {
        val results = mutableListOf<BatchItemResult>()
        var processed = 0
        onProgress(BatchProgress(0, items.size))

        for (item in items) {
            if (isCancelled()) break
            processed += 1
            onProgress(BatchProgress(processed, items.size, item.displayName))

            val result = runCatching {
                val existing = readFields(item.uri)
                val updated = BatchOps.apply(existing, edit)
                if (updated == existing) {
                    BatchItemResult(item.uri, item.displayName, success = true, message = BatchItemResult.UNCHANGED)
                } else {
                    val error = writeFields(item.uri, updated)
                    BatchItemResult(item.uri, item.displayName, success = error == null, message = error)
                }
            }.getOrElse { error ->
                BatchItemResult(item.uri, item.displayName, success = false, message = error.message ?: "failed")
            }
            results += result
        }
        return results
    }

    /** Convenience summary for the completion notification. */
    fun summarize(results: List<BatchItemResult>): String {
        val failed = results.count { !it.success }
        val unchanged = results.count { it.success && it.unchanged }
        val written = results.size - failed - unchanged
        return buildString {
            append("$written written")
            if (unchanged > 0) append(", $unchanged unchanged")
            if (failed > 0) append(", $failed failed")
        }
    }
}
