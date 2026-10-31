/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.io

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

/**
 * The "ignore these directories" preference.
 *
 * The system folder picker returns a tree URI whose document id is `volume:relative/path`
 * (`primary:Music/Ringtone`). MediaStore exposes `RELATIVE_PATH` in the same shape, so the
 * part after the colon is what the filter compares against.
 */
object IgnoreDirectory {

    fun relativePath(treeUri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        val path = documentId.substringAfter(':', "").trim().trim('/')
        return path.ifEmpty { null }
    }

    /** Keeps the tree permission alive so the stored path keeps working after a reboot. */
    fun persist(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /** Appends [path] to the newline-separated preference, ignoring duplicates. */
    fun add(existing: String, path: String): String {
        val current = existing.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (current.any { it.equals(path, ignoreCase = true) }) return current.joinToString("\n")
        return (current + path).joinToString("\n")
    }
}
