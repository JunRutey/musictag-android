/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.io

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import java.io.File

/**
 * Getting an edited file back to where it came from.
 *
 * `file://` documents were edited in place. `content://` documents were staged into the
 * cache so TagLib could open a path (see [DocumentSource]); the staged copy is written
 * back through the ContentResolver with mode `rwt` (read-write-truncate), which is what
 * providers expect for a full replacement.
 */
object DocumentWriter {

    sealed interface Result {
        data object Success : Result

        /**
         * The provider refused the write because this app does not own the document. On
         * Android 10+ `MediaStore.createWriteRequest` asks the user once and then the same
         * write succeeds, so this is recoverable rather than an error.
         */
        data object NeedsPermission : Result
        data class Failure(val message: String) : Result
    }

    fun writeBack(context: Context, uri: Uri, document: LocalDocument): Result {
        if (!document.staged) {
            // Already written in place.
            refreshMediaStore(context, document.path)
            return Result.Success
        }
        return try {
            val source = File(document.path)
            // A provider that refuses here throws SecurityException, handled below.
            val stream = context.contentResolver.openOutputStream(uri, "rwt")
                ?: return Result.Failure("the provider did not open ${uri.authority} for writing")
            stream.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }
            refreshMediaStore(context, document.path)
            source.delete()
            Result.Success
        } catch (t: Throwable) {
            if (t is SecurityException) {
                Result.NeedsPermission
            } else {
                Result.Failure("write-back failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /**
     * Tells MediaStore about the change.
     *
     * Files reached through `file://` (SD card, USB storage) are indexed by path, so a
     * scan is required or galleries keep the stale date/size. Documents written through a
     * provider update their own index.
     */
    private fun refreshMediaStore(context: Context, path: String) {
        try {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        } catch (ignored: Throwable) {
            // Scanning is best effort; a failure must not turn a successful write into an error.
        }
    }
}
