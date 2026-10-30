/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.app.Activity
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
import com.xjcheng.musictageditor.core.tag.PictureEdit
import com.xjcheng.musictageditor.core.tag.TagData
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.core.tag.TagEdit
import com.xjcheng.musictageditor.core.tag.TagLibEngine
import com.xjcheng.musictageditor.core.tag.TagPicture
import com.xjcheng.musictageditor.core.tag.WriteResult
import com.xjcheng.musictageditor.activity.SearchActivity
import com.xjcheng.musictageditor.core.net.TagCandidate
import com.xjcheng.musictageditor.io.FileOps
import com.xjcheng.musictageditor.io.LyricsSidecar
import com.xjcheng.musictageditor.service.RefreshMediaStoreService
import com.xjcheng.musictageditor.io.CoverDownloader
import com.xjcheng.musictageditor.io.DocumentSource
import com.xjcheng.musictageditor.io.DocumentWriter
import com.xjcheng.musictageditor.net.SearchContract
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
    private val coverDownloader = CoverDownloader()

    private val findTagsOnline =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data ?: return@registerForActivityResult
            applyCandidate(SearchContract.read(data))
        }

    private val pickCover = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                ui = ui.copy(error = getString(R.string.editor_error_read_image))
            } else {
                coverMime = contentResolver.getType(uri) ?: "image/png"
                ui = ui.copy(coverBytes = bytes, coverRemoved = false, coverDirty = true, error = null)
            }
        }
    }

    private val pickArtistCover = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null || bytes.isEmpty()) {
                ui = ui.copy(error = getString(R.string.editor_error_read_image))
            } else {
                ui = ui.copy(
                    artistCoverBytes = bytes,
                    artistCoverMime = contentResolver.getType(uri) ?: "image/png",
                    picturesDirty = true,
                    error = null,
                )
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
                    onPickArtistCover = { pickArtistCover.launch("image/*") },
                    onRemoveArtistCover = {
                        ui = ui.copy(artistCoverBytes = null, picturesDirty = true)
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
                    onRename = ::renameTo,
                    onDelete = ::deleteFile,
                    onShare = ::shareFile,
                    onSearchOnline = { openOnlineSearch() },
                    onBack = { finish() },
                )
            }
        }

        val uri = resolveDocumentUri(intent)
        if (uri == null) {
            ui = ui.copy(loading = false, error = getString(R.string.editor_error_no_document))
        } else {
            documentUri = uri
            load(uri)
        }
    }

    private fun applyField(field: TagField, value: String) {
        ui = ui.copy(edit = field.write(ui.edit, value))
    }

    private fun persistSettings() {
        val updated = settings.load()
            .with("write_id3v1", ui.writeId3v1)
            .with("id3v2_mode", if (ui.id3v23) "3" else "4")
        settings.save(updated)
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

    /** Opens the online search prefilled with the current values. */
    private fun openOnlineSearch() {
        val extras = SearchContract.request(ui.edit.title, ui.edit.artist, ui.edit.album)
        val intent = Intent(this, SearchActivity::class.java)
        intent.putExtras(extras.extras ?: android.os.Bundle())
        findTagsOnline.launch(intent)
    }

    /**
     * Applies an online candidate to the open draft.
     *
     * Only fields the provider actually returned are touched, so a lyrics-only source
     * cannot wipe the other values. The cover, when offered, is downloaded separately and
     * marked dirty so saving writes it.
     */
    private fun applyCandidate(candidate: TagCandidate) {
        val edit = ui.edit.copy(
            title = candidate.title ?: ui.edit.title,
            artist = candidate.artist ?: ui.edit.artist,
            album = candidate.album ?: ui.edit.album,
            albumArtist = candidate.albumArtist ?: ui.edit.albumArtist,
            genre = candidate.genre ?: ui.edit.genre,
            composer = candidate.composer ?: ui.edit.composer,
            track = candidate.track ?: ui.edit.track,
            disc = candidate.disc ?: ui.edit.disc,
            year = candidate.year ?: ui.edit.year,
            comment = candidate.comment ?: ui.edit.comment,
            lyrics = candidate.lyrics ?: ui.edit.lyrics,
        )
        ui = ui.copy(edit = edit, message = getString(R.string.editor_message_tags_filled))
        candidate.coverUrl?.let(::downloadCover)
    }

    private fun downloadCover(url: String) {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) { coverDownloader.fetch(url) }
            if (bytes == null || bytes.isEmpty()) {
                ui = ui.copy(message = getString(R.string.editor_message_tags_filled_no_cover))
            } else {
                coverMime = if (bytes.size > 3 && bytes[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
                ui = ui.copy(
                    coverBytes = bytes,
                    coverDirty = true,
                    coverRemoved = false,
                    message = getString(R.string.editor_message_tags_and_cover),
                )
            }
        }
    }

    private fun load(uri: Uri) {
        lifecycleScope.launch {
            ui = ui.copy(loading = true, error = null, message = null)
            val result = withContext(Dispatchers.IO) {
                val resolved = DocumentSource.resolve(this@SongDetailActivity, uri)
                    ?: return@withContext LoadResult.Failed(
                        getString(R.string.editor_error_open_document)
                    )
                val tag = engine.read(resolved.path)
                    ?: return@withContext LoadResult.Failed(getString(R.string.editor_error_unknown_container))
                LoadResult.Ok(resolved, tag)
            }
            when (result) {
                is LoadResult.Failed -> ui = ui.copy(loading = false, error = result.message)
                is LoadResult.Ok -> {
                    document = result.document
                    val tag = result.tag
                    // A tag without lyrics falls back to the ".lrc" side file, if we can
                    // reach the real path next to it.
                    val sidecarLyrics = if (tag.lyrics.isBlank()) {
                        LyricsSidecar.pathFor(this@SongDetailActivity, uri)?.let { audioPath ->
                            LyricsSidecar.read(
                                audioPath = audioPath,
                                settings = settings.load(),
                                title = tag.title,
                                artist = tag.artist,
                                album = tag.album,
                            )
                        }
                    } else {
                        null
                    }
                    val front = tag.pictures.firstOrNull { it.type == TagPicture.FRONT_COVER }
                        ?: tag.pictures.firstOrNull()
                    val artist = tag.pictures.firstOrNull { it.type == TagPicture.ARTIST }
                    coverMime = front?.mimeType ?: "image/png"
                    ui = EditorUiState(
                        documentName = result.document.displayName,
                        container = tag.container,
                        tagFormat = tag.tagFormat,
                        audio = describeAudio(tag),
                        edit = TagEdit.from(tag).let { edit ->
                            if (sidecarLyrics != null) edit.copy(lyrics = sidecarLyrics) else edit
                        },
                        original = tag,
                        coverBytes = front?.data,
                        artistCoverBytes = artist?.data,
                        artistCoverMime = artist?.mimeType ?: "image/png",
                        supportsTypedPictures = tag.container !in SINGLE_COVER_CONTAINERS,
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
        // Any typed-picture edit is written as a complete set, so the front cover and the
        // artist image cannot silently drop each other.
        val pictures: List<PictureEdit>? =
            if (ui.picturesDirty || ui.artistCoverBytes != null) {
                buildList<PictureEdit> {
                if (!ui.coverRemoved) {
                    ui.coverBytes?.let { add(PictureEdit(it, coverMime, TagPicture.FRONT_COVER)) }
                }
                ui.artistCoverBytes?.let {
                    add(PictureEdit(it, ui.artistCoverMime, TagPicture.ARTIST))
                }
            }
        } else {
            null
        }
        val edit = ui.edit.copy(
            cover = if (pictures != null) CoverEdit.Unchanged else coverEdit,
            pictures = pictures,
            writeId3v1 = ui.writeId3v1,
            id3v23 = ui.id3v23,
        )

        lifecycleScope.launch {
            ui = ui.copy(saving = true, error = null, message = null)
            val failure = withContext(Dispatchers.IO) {
                when (val write = engine.write(current.path, edit)) {
                    is WriteResult.Failure -> getString(R.string.editor_error_write_failed, write.message.orEmpty())
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
                ui = ui.copy(message = getString(R.string.editor_message_saved))
                writeLyricsSidecar(uri)
                load(uri)
            }
        }
    }

    /** Writes the ".lrc" side file when the setting is on and the real path is reachable. */
    private fun writeLyricsSidecar(uri: Uri) {
        val preferences = settings.load()
        if (!preferences.saveLrcWithTags) return
        val audioPath = LyricsSidecar.pathFor(this, uri) ?: return
        LyricsSidecar.write(
            audioPath = audioPath,
            lyrics = ui.edit.lyrics,
            settings = preferences,
            title = ui.edit.title,
            artist = ui.edit.artist,
            album = ui.edit.album,
        )
    }

    private fun renameTo(name: String) {
        val current = document ?: return
        val uri = documentUri ?: return
        when (FileOps.rename(this, uri, name, current.displayName)) {
            FileOps.Result.Ok -> load(uri)
            is FileOps.Result.Failed -> ui = ui.copy(error = getString(R.string.editor_error_rename))
        }
    }

    private fun deleteFile() {
        val current = document ?: return
        val uri = documentUri ?: return
        when (FileOps.delete(this, uri)) {
            FileOps.Result.Ok -> {
                RefreshMediaStoreService.start(this, listOf(current.path))
                finish()
            }
            is FileOps.Result.Failed -> ui = ui.copy(error = getString(R.string.editor_error_delete))
        }
    }

    private fun shareFile() {
        document ?: return
        val uri = documentUri ?: return
        val mimeType = contentResolver.getType(uri) ?: "audio/*"
        when (FileOps.share(this, uri, mimeType)) {
            FileOps.Result.Ok -> Unit
            is FileOps.Result.Failed -> ui = ui.copy(error = getString(R.string.editor_error_share))
        }
    }
}

/** Containers that hold one cover item only, so no artist image can be stored. */
private val SINGLE_COVER_CONTAINERS = setOf("MP4", "ASF")
