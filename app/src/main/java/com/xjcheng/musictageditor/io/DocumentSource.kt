/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * A document handed to us by another app, resolved to something the tag engine can open.
 *
 * TagLib works on filesystem paths, so `content://` documents are staged into the app
 * cache first. The copy is what gets parsed; M2 decides whether to edit in place
 * (`openAssetFileDescriptor("rwt")`) or edit the copy and write it back through the
 * ContentResolver.
 */
data class LocalDocument(
    val path: String,
    val displayName: String,
    val staged: Boolean,
)

object DocumentSource {

    private const val INCOMING_DIR = "incoming"

    /**
     * Returns null when the URI cannot be turned into a readable file (unsupported
     * scheme, missing permission, empty document).
     */
    fun resolve(context: Context, uri: Uri): LocalDocument? = when (uri.scheme) {
        "file" -> uri.path?.let { path ->
            val file = File(path)
            if (file.isFile && file.canRead()) LocalDocument(path, file.name, staged = false) else null
        }
        "content" -> stage(context, uri)
        else -> null
    }

    /** Deletes staged copies; safe to call at any time. */
    fun clearStaged(context: Context) {
        File(context.cacheDir, INCOMING_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun stage(context: Context, uri: Uri): LocalDocument? {
        val name = displayName(context, uri) ?: "document"
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(120).ifEmpty { "document" }
        val dir = File(context.cacheDir, INCOMING_DIR).apply { mkdirs() }
        // One slot per name keeps repeated opens from filling the cache.
        val target = File(dir, safeName)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (target.isFile && target.length() > 0) {
                LocalDocument(target.absolutePath, name, staged = true)
            } else {
                null
            }
        } catch (t: Throwable) {
            target.delete()
            null
        }
    }

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    } catch (t: Throwable) {
        null
    }
}
