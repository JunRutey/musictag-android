/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.io

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * A document handed to us by another app, resolved to something the tag engine can open.
 *
 * TagLib works on filesystem paths, so `content://` documents are staged into the app cache
 * first. The copy is what gets parsed and edited; the result is written back through the
 * ContentResolver.
 */
data class LocalDocument(
    val path: String,
    val displayName: String,
    val staged: Boolean,
)

/** Why a document could not be staged, so the caller can say something useful. */
enum class ResolveFailure { NO_PERMISSION, UNREADABLE, EMPTY_FILE, UNSUPPORTED_URI }

sealed interface ResolveResult {
    data class Ok(val document: LocalDocument) : ResolveResult
    data class Failed(val reason: ResolveFailure) : ResolveResult
}

object DocumentSource {

    private const val INCOMING_DIR = "incoming"

    /** Convenience wrapper for callers that only need success or failure. */
    fun resolve(context: Context, uri: Uri): LocalDocument? =
        (resolveDetailed(context, uri) as? ResolveResult.Ok)?.document

    /**
     * Stages [uri] into the cache and reports *why* it failed when it does.
     *
     * A `content://media/...` row is readable from the media permission alone, but a document
     * from another provider can still refuse; both a `SecurityException` and a null stream
     * are distinguished here instead of collapsing into "no document".
     */
    fun resolveDetailed(context: Context, uri: Uri): ResolveResult = when (uri.scheme) {
        "file" -> uri.path?.let { path ->
            val file = File(path)
            when {
                file.isFile && file.canRead() && file.length() > 0 ->
                    ResolveResult.Ok(LocalDocument(path, file.name, staged = false))
                file.isFile && file.canRead() -> ResolveResult.Failed(ResolveFailure.EMPTY_FILE)
                else -> ResolveResult.Failed(ResolveFailure.UNREADABLE)
            }
        } ?: ResolveResult.Failed(ResolveFailure.UNSUPPORTED_URI)
        "content" -> stage(context, uri)
        else -> ResolveResult.Failed(ResolveFailure.UNSUPPORTED_URI)
    }

    /** Deletes staged copies; safe to call at any time. */
    fun clearStaged(context: Context) {
        File(context.cacheDir, INCOMING_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun stage(context: Context, uri: Uri): ResolveResult {
        val name = displayName(context, uri) ?: "document"
        val target = File(File(context.cacheDir, INCOMING_DIR).apply { mkdirs() }, stagedName(uri, name))
        val stream = try {
            openStream(context, uri)
        } catch (error: SecurityException) {
            return ResolveResult.Failed(ResolveFailure.NO_PERMISSION)
        }
        if (stream == null) return ResolveResult.Failed(ResolveFailure.UNREADABLE)

        return try {
            stream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.isFile && target.length() > 0) {
                ResolveResult.Ok(LocalDocument(target.absolutePath, name, staged = true))
            } else {
                target.delete()
                ResolveResult.Failed(ResolveFailure.EMPTY_FILE)
            }
        } catch (error: Throwable) {
            target.delete()
            ResolveResult.Failed(ResolveFailure.UNREADABLE)
        }
    }

    /**
     * A cache file name that cannot collide.
     *
     * The display name is sanitised to ASCII for the filesystem, which turns every Chinese
     * title into underscores; the URI hash keeps two such documents apart, and the extension
     * is preserved so the container stays recognisable on disk.
     */
    private fun stagedName(uri: Uri, displayName: String): String {
        val extension = displayName.substringAfterLast('.', "")
            .takeIf { it.isNotEmpty() && it.length <= 8 && it.all { c -> c.isLetterOrDigit() } }
        val base = displayName.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(48)
            .ifEmpty { "audio" }
        val slot = Integer.toHexString(uri.toString().hashCode())
        val suffix = if (extension != null) ".$extension" else ""
        return "$base-$slot$suffix"
    }

    /** `openInputStream`, then a file-descriptor fallback for providers that refuse it. */
    private fun openStream(context: Context, uri: Uri) =
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            ?: runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.let {
                    ParcelFileDescriptor.AutoCloseInputStream(it)
                }
            }.getOrNull()

    private fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
    } catch (error: Throwable) {
        null
    }
}
