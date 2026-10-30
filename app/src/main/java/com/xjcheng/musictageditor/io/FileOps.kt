/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.io

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore

/**
 * File-level actions on one library entry: rename, delete and share.
 *
 * MediaStore rows are updated through `ContentResolver`, while documents picked from a
 * provider go through `DocumentsContract`; both paths refresh the media database so the
 * change is visible without a reboot.
 */
object FileOps {

    sealed interface Result {
        data object Ok : Result
        data class Failed(val message: String) : Result
    }

    /**
     * Renames [uri] to [requested].
     *
     * A name without an extension keeps the current one, because dropping it would make the
     * file unplayable and the extension is not something the row editor shows.
     */
    fun rename(context: Context, uri: Uri, requested: String, currentName: String): Result {
        val trimmed = requested.trim()
        if (trimmed.isEmpty()) return Result.Failed("empty name")
        val newName = if (trimmed.contains('.') && trimmed.substringAfterLast('.').isNotEmpty()) {
            trimmed
        } else {
            val extension = currentName.substringAfterLast('.', "")
            if (extension.isEmpty()) trimmed else "$trimmed.$extension"
        }
        if (newName == currentName) return Result.Ok

        val resolver = context.contentResolver
        val ok = runCatching {
            if (isDocumentUri(uri)) {
                DocumentsContract.renameDocument(resolver, uri, newName) != null
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
                }
                resolver.update(uri, values, null, null) > 0
            }
        }.getOrDefault(false)

        return if (ok) {
            scan(context, mediaStorePath(context, uri))
            Result.Ok
        } else {
            Result.Failed("rename failed")
        }
    }

    /** Deletes the file behind [uri], preferring the provider that owns it. */
    fun delete(context: Context, uri: Uri): Result {
        val resolver = context.contentResolver
        val ok = runCatching {
            if (isDocumentUri(uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
            } else {
                resolver.delete(uri, null, null) > 0
            }
        }.getOrDefault(false)
        return if (ok) Result.Ok else Result.Failed("delete failed")
    }

    /** Opens the system share sheet with a temporary read grant. */
    fun share(context: Context, uri: Uri, mimeType: String): Result {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "audio/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            Result.Ok
        } catch (error: ActivityNotFoundException) {
            Result.Failed(error.message ?: "no app can share this file")
        }
    }

    /** Asks the media scanner to re-read one path, if it is a real file path. */
    fun scan(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
        }
    }

    private fun isDocumentUri(uri: Uri): Boolean =
        uri.authority?.contains(".documents") == true

    /** MediaStore exposes DATA on older releases only; the scan is best effort either way. */
    private fun mediaStorePath(context: Context, uri: Uri): String? {
        if (!uri.authority.orEmpty().startsWith("media")) return null
        return runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
    }
}
