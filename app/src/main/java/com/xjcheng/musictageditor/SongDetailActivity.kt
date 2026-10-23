/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.xjcheng.musictageditor.core.tag.CoverEdit
import com.xjcheng.musictageditor.core.tag.TagData
import com.xjcheng.musictageditor.core.tag.TagEdit
import com.xjcheng.musictageditor.core.tag.TagLibEngine
import com.xjcheng.musictageditor.core.tag.WriteResult
import com.xjcheng.musictageditor.io.DocumentSource
import com.xjcheng.musictageditor.io.DocumentWriter
import com.xjcheng.musictageditor.io.LocalDocument
import com.xjcheng.musictageditor.settings.Settings
import com.xjcheng.musictageditor.settings.SettingsRepository
import com.xjcheng.musictageditor.ui.editor.EditorUiState
import com.xjcheng.musictageditor.ui.editor.TagEditorScreen
import com.xjcheng.musictageditor.ui.editor.TagField
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The tag editor.
 *
 * Third-party apps reach this activity through `VIEW`/`SEND` intents (see docs/COMPAT.md);
 * the document they hand over is resolved through the ContentResolver, staged if needed,
 * and edited with the native TagLib engine. Saving writes the staged copy back through
 * the same URI, which is the part the original app could not do for most providers.
 */
class SongDetailActivity : ComponentActivity() {

    private val engine = TagLibEngine()
    private val settings by lazy { SettingsRepository(this) }

    private var ui by mutableStateOf(EditorUiState())
    private var document: LocalDocument? = null
    private var documentUri: Uri? = null
    private var coverMime: String = "image/png"

    private val pickCover = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                ui = ui.copy(error = "could not read the selected image")
            } else {
                coverMime = contentResolver.getType(uri) ?: "image/png"
                ui = ui.copy(coverBytes = bytes, coverRemoved = false, coverDirty = true, error = null)
            }
        }
    }

    private sealed interface LoadResult {
        data class Ok(val document: LocalDocument, val tag: TagData) : LoadResult
        data class Failed(val message: String) : LoadResult
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialSettings = settings.load()
        ui = ui.copy(writeId3v1 = initialSettings.writeId3v1, id3v23 = initialSettings.id3v23)

        setContent {
            MusicTagTheme {
                TagEditorScreen(
                    state = ui,
                    onFieldChange = { field, value -> applyField(field, value) },
                    onLyricsChange = { value -> ui = ui.copy(edit = ui.edit.copy(lyrics = value)) },
                    onPickCover = { pickCover.launch("image/*") },
                    onRemoveCover = {
                        ui = ui.copy(coverBytes = null, coverRemoved = true, coverDirty = true)
                    },
                    onWriteId3v1Change = { checked ->
                        ui = ui.copy(writeId3v1 = checked)
                        persistSettings()
                    },
                    onId3v23Change = { checked ->
                        ui = ui.copy(id3v23 = checked)
                        persistSettings()
                    },
                    onSave = { save() },
                )
            }
        }

        val uri = resolveDocumentUri(intent)
        if (uri == null) {
            ui = ui.copy(loading = false, error = "This activity expects an audio document (VIEW or SEND).")
        } else {
            documentUri = uri
            load(uri)
        }
    }

    private fun applyField(field: TagField, value: String) {
        ui = ui.copy(edit = field.write(ui.edit, value))
    }

    private fun persistSettings() {
        settings.save(Settings(writeId3v1 = ui.writeId3v1, id3v23 = ui.id3v23, dffId3 = true))
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

    private fun load(uri: Uri) {
        lifecycleScope.launch {
            ui = ui.copy(loading = true, error = null, message = null)
            val result = withContext(Dispatchers.IO) {
                val resolved = DocumentSource.resolve(this@SongDetailActivity, uri)
                    ?: return@withContext LoadResult.Failed(
                        "cannot open this document (no permission or empty file)"
                    )
                val tag = engine.read(resolved.path)
                    ?: return@withContext LoadResult.Failed("not a recognised audio container")
                LoadResult.Ok(resolved, tag)
            }
            when (result) {
                is LoadResult.Failed -> ui = ui.copy(loading = false, error = result.message)
                is LoadResult.Ok -> {
                    document = result.document
                    val tag = result.tag
                    coverMime = tag.pictures.firstOrNull()?.mimeType ?: "image/png"
                    ui = EditorUiState(
                        documentName = result.document.displayName,
                        container = tag.container,
                        tagFormat = tag.tagFormat,
                        audio = describeAudio(tag),
                        edit = TagEdit.from(tag),
                        original = tag,
                        coverBytes = tag.pictures.firstOrNull()?.data,
                        isMp3 = tag.container == "MPEG",
                        writeId3v1 = ui.writeId3v1,
                        id3v23 = ui.id3v23 || tag.tagFormat.startsWith("ID3v2.3"),
                        loading = false,
                    )
                }
            }
        }
    }

    private fun describeAudio(tag: TagData): String = buildString {
        if (tag.audio.durationMs > 0) append("${tag.audio.durationMs / 1000}s")
        if (tag.audio.sampleRateHz > 0) {
            if (isNotEmpty()) append(" · ")
            append("${tag.audio.sampleRateHz} Hz")
        }
        if (tag.audio.channels > 0) {
            if (isNotEmpty()) append(" · ")
            append("${tag.audio.channels} ch")
        }
        if (tag.audio.bitsPerSample > 0) {
            if (isNotEmpty()) append(" · ")
            append("${tag.audio.bitsPerSample} bit")
        }
    }

    private fun save() {
        val current = document ?: return
        val uri = documentUri ?: return
        val coverEdit = when {
            ui.coverRemoved -> CoverEdit.Remove
            ui.coverDirty -> ui.coverBytes?.let { CoverEdit.Replace(it, coverMime) } ?: CoverEdit.Remove
            else -> CoverEdit.Unchanged
        }
        val edit = ui.edit.copy(
            cover = coverEdit,
            writeId3v1 = ui.writeId3v1,
            id3v23 = ui.id3v23,
        )

        lifecycleScope.launch {
            ui = ui.copy(saving = true, error = null, message = null)
            val failure = withContext(Dispatchers.IO) {
                when (val write = engine.write(current.path, edit)) {
                    is WriteResult.Failure -> "write failed: ${write.message}"
                    is WriteResult.Success -> {
                        when (val back = DocumentWriter.writeBack(this@SongDetailActivity, uri, current)) {
                            is DocumentWriter.Result.Success -> null
                            is DocumentWriter.Result.Failure -> back.message
                        }
                    }
                }
            }
            ui = ui.copy(saving = false)
            if (failure != null) {
                ui = ui.copy(error = failure)
            } else {
                ui = ui.copy(message = "Saved")
                load(uri)
            }
        }
    }
}
