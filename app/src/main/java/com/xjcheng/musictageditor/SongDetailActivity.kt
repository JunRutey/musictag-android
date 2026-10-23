/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xjcheng.musictageditor.activity.PlaceholderActivity
import com.xjcheng.musictageditor.core.tag.TagLibEngine
import com.xjcheng.musictageditor.io.DocumentSource
import com.xjcheng.musictageditor.ui.M0Screen
import com.xjcheng.musictageditor.ui.describeIntent
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme

/**
 * Tag editor screen — the app's main compatibility entry point.
 *
 * Third parties open their audio documents here via VIEW (typed audio MIME, or the
 * any-any MIME filter with extension matching) or SEND. M0 resolves and *probes* the
 * handed-over document so the read/write path is validated early; the editor itself
 * lands in M2.
 */
class SongDetailActivity : ComponentActivity() {

    private val engine = TagLibEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PlaceholderActivity.trace("SongDetailActivity", intent)
        render(intent)
    }

    private fun render(intent: Intent?) {
        val lines = describeIntent(intent).toMutableList()
        lines += "grantFlags: 0x${(intent?.flags ?: 0).toString(16)}"
        val uri = resolveDocumentUri(intent)
        lines += ""
        if (uri == null) {
            lines += "document: <none — nothing to edit yet>"
        } else {
            lines += probeDocument(uri)
            lines += ""
            lines += readTags(uri)
        }
        setContent {
            MusicTagTheme {
                M0Screen(
                    title = "Edit tags",
                    milestone = "M1 — tag engine reads files; the editor lands in M2",
                    lines = lines,
                )
            }
        }
    }

    /**
     * M1 proof that the native tag engine works in the shipped APK: read the document
     * and show what TagLib found. Editing arrives in M2.
     */
    private fun readTags(uri: Uri): List<String> {
        val document = DocumentSource.resolve(this, uri)
            ?: return listOf("tags: <document could not be opened>")
        return try {
            val tag = engine.read(document.path)
            if (tag == null) {
                listOf("tags: <not a recognised audio container>")
            } else {
                buildList {
                    add("tags: ${tag.summary()}")
                    add("  title:   ${tag.title}")
                    add("  artist:  ${tag.artist}")
                    add("  album:   ${tag.album}")
                    add("  track:   ${tag.trackStr}")
                    add("  year:    ${tag.year}")
                    add("  lyrics:  ${tag.lyrics.length} chars")
                    add("  format:  ${tag.tagFormat} (taglib ${engine.version()})")
                }
            }
        } catch (t: Throwable) {
            listOf("tags: <engine error: ${t.javaClass.simpleName}: ${t.message}>")
        } finally {
            if (document.staged) {
                // The staged copy is only needed for this read; M2 keeps it while editing.
                java.io.File(document.path).delete()
            }
        }
    }

    private fun resolveDocumentUri(intent: Intent?): Uri? {
        intent ?: return null
        intent.data?.let { return it }
        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (stream != null) return stream
        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) return clip.getItemAt(0).uri
        return null
    }

    /**
     * Read/write capability probe.
     *
     * The original app converted `content://` to a filesystem path, which fails for
     * every provider that is not backed by `_data`. We instead resolve through
     * ContentResolver so this works for MediaStore, SAF, and Downloads providers.
     */
    private fun probeDocument(uri: Uri): List<String> {
        val out = mutableListOf<String>()
        out += "document: $uri"
        val resolver = contentResolver
        when (uri.scheme) {
            "content" -> {
                try {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                        ?.use { c ->
                            if (c.moveToFirst()) {
                                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                                if (nameIdx >= 0 && !c.isNull(nameIdx)) out += "displayName: ${c.getString(nameIdx)}"
                                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) out += "size: ${c.getLong(sizeIdx)} bytes"
                            }
                        }
                } catch (e: Exception) {
                    out += "query failed: ${e.javaClass.simpleName}"
                }
                out += "readable: ${canOpen(uri, "r")}"
                out += "writable: ${canOpen(uri, "rw") || canOpen(uri, "rwt")}"
            }
            "file" -> {
                val path = uri.path
                if (path != null) {
                    val f = java.io.File(path)
                    out += "path: $path"
                    out += "exists: ${f.exists()}"
                    out += "size: ${if (f.exists()) f.length() else -1} bytes"
                    out += "readable: ${f.canRead()}"
                    out += "writable: ${f.canWrite()}"
                }
            }
            else -> out += "unsupported scheme: ${uri.scheme}"
        }
        return out
    }

    private fun canOpen(uri: Uri, mode: String): Boolean = try {
        contentResolver.openAssetFileDescriptor(uri, mode)?.use { true } ?: false
    } catch (e: Exception) {
        false
    }
}
