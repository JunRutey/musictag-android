/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.core.tag.TagData
import com.xjcheng.musictageditor.core.tag.TagEdit
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Delete
import top.yukonga.miuix.kmp.icon.icons.useful.Save
import top.yukonga.miuix.kmp.icon.icons.useful.Share
import top.yukonga.miuix.kmp.icon.icons.useful.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The ten editable fields, in the same order as the original editor. */
enum class TagField(
    val label: String,
    val read: (TagEdit) -> String,
    val write: (TagEdit, String) -> TagEdit,
) {
    TITLE("标题", { it.title }, { edit, value -> edit.copy(title = value) }),
    ARTIST("艺术家", { it.artist }, { edit, value -> edit.copy(artist = value) }),
    ALBUM("专辑", { it.album }, { edit, value -> edit.copy(album = value) }),
    ALBUM_ARTIST("专辑艺术家", { it.albumArtist }, { edit, value -> edit.copy(albumArtist = value) }),
    GENRE("流派", { it.genre }, { edit, value -> edit.copy(genre = value) }),
    COMPOSER("作曲", { it.composer }, { edit, value -> edit.copy(composer = value) }),
    TRACK("音轨（n 或 n/m）", { it.track }, { edit, value -> edit.copy(track = value) }),
    DISC("碟号（n 或 n/m）", { it.disc }, { edit, value -> edit.copy(disc = value) }),
    YEAR("年份", { it.year }, { edit, value -> edit.copy(year = value) }),
    COMMENT("注释", { it.comment }, { edit, value -> edit.copy(comment = value) }),
}

/** Everything the editor renders. Held by the activity so rotation keeps the draft. */
data class EditorUiState(
    val documentName: String = "",
    val container: String = "",
    val tagFormat: String = "",
    val audio: String = "",
    val edit: TagEdit = TagEdit(),
    val original: TagData? = null,
    val coverBytes: ByteArray? = null,
    /** The user picked or removed artwork, so the write must touch the cover. */
    val coverDirty: Boolean = false,
    val coverRemoved: Boolean = false,
    /** A second, typed picture written as the artist image. */
    val artistCoverBytes: ByteArray? = null,
    val artistCoverMime: String = "image/png",
    /** Any picture change at all: the write then replaces the whole typed picture set. */
    val picturesDirty: Boolean = false,
    /** False for containers that hold a single cover (MP4, ASF). */
    val supportsTypedPictures: Boolean = true,
    val isMp3: Boolean = false,
    val writeId3v1: Boolean = false,
    val id3v23: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

/**
 * The tag editor, laid out as Miuix rows: a top bar with search-and-save actions, the file
 * facts, one inline field per tag, the lyrics editor, the artwork row and, for MP3, the two
 * write options.
 */
@Composable
fun TagEditorScreen(
    state: EditorUiState,
    onFieldChange: (TagField, String) -> Unit,
    onLyricsChange: (String) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onPickArtistCover: () -> Unit,
    onRemoveArtistCover: () -> Unit,
    onWriteId3v1Change: (Boolean) -> Unit,
    onId3v23Change: (Boolean) -> Unit,
    onSave: () -> Unit,
    onSearchOnline: () -> Unit,
    onRename: (String) -> Unit = {},
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
) {
    var renameText by rememberSaveable(state.documentName) {
        mutableStateOf(state.documentName)
    }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = state.documentName.ifBlank { stringResource(R.string.editor_title_fallback) },
                actions = {
                    IconButton(onClick = onSearchOnline) {
                        Icon(MiuixIcons.Useful.Search, stringResource(R.string.action_search_online))
                    }
                    IconButton(onClick = onShare) {
                        Icon(MiuixIcons.Useful.Share, stringResource(R.string.action_share))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(MiuixIcons.Useful.Delete, stringResource(R.string.action_delete))
                    }
                    IconButton(onClick = onSave, enabled = !state.saving) {
                        Icon(MiuixIcons.Useful.Save, stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll),
        ) {
            val facts = listOf(state.container, state.tagFormat, state.audio)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (facts.isNotBlank()) {
                Text(
                    text = facts,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            if (state.loading) {
                Text(
                    text = stringResource(R.string.editor_loading),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }
            if (state.saving) {
                Text(
                    text = stringResource(R.string.editor_saving),
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            for (field in TagField.entries) {
                BasicComponent(
                    title = field.label,
                    bottomAction = {
                        InlineField(
                            value = field.read(state.edit),
                            onValueChange = { onFieldChange(field, it) },
                        )
                    },
                )
            }

            BasicComponent(
                title = stringResource(R.string.label_lyrics),
                summary = state.edit.lyrics.lines().size.let {
                    stringResource(R.string.editor_lyrics_lines, it)
                },
                bottomAction = {
                    InlineField(
                        value = state.edit.lyrics,
                        onValueChange = onLyricsChange,
                        singleLine = false,
                        maxLines = 8,
                    )
                },
            )

            CoverRow(state = state, onPickCover = onPickCover, onRemoveCover = onRemoveCover)

            if (state.supportsTypedPictures) {
                ArtistCoverRow(
                    state = state,
                    onPick = onPickArtistCover,
                    onRemove = onRemoveArtistCover,
                )
            } else {
                Text(
                    text = stringResource(R.string.editor_picture_single_container, state.container),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            if (state.isMp3) {
                BasicComponent(
                    title = stringResource(R.string.editor_write_id3v1),
                    rightActions = {
                        Switch(checked = state.writeId3v1, onCheckedChange = onWriteId3v1Change)
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.editor_use_id3v23),
                    rightActions = {
                        Switch(checked = state.id3v23, onCheckedChange = onId3v23Change)
                    },
                )
            }

            BasicComponent(
                title = stringResource(R.string.action_save),
                enabled = !state.saving,
                onClick = onSave,
            )

            BasicComponent(
                title = stringResource(R.string.editor_rename),
                summary = state.documentName,
                bottomAction = {
                    InlineField(value = renameText, onValueChange = { renameText = it })
                },
            )
            BasicComponent(
                title = stringResource(R.string.editor_rename_confirm),
                enabled = renameText.isNotBlank() && renameText != state.documentName,
                onClick = { onRename(renameText) },
            )

            if (confirmDelete) {
                BasicComponent(
                    title = stringResource(R.string.editor_delete_confirm),
                    summary = state.documentName,
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                )
                BasicComponent(
                    title = stringResource(R.string.action_cancel),
                    onClick = { confirmDelete = false },
                )
            } else {
                BasicComponent(
                    title = stringResource(R.string.editor_delete),
                    onClick = { confirmDelete = true },
                )
            }
        }
    }
}

@Composable
private fun CoverRow(
    state: EditorUiState,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
) {
    val bitmap = remember(state.coverBytes) {
        state.coverBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val status = when {
        state.coverRemoved -> stringResource(R.string.editor_cover_removed)
        state.coverDirty -> stringResource(R.string.editor_cover_changed)
        bitmap != null -> stringResource(R.string.editor_cover_present)
        else -> stringResource(R.string.editor_cover_none)
    }

    BasicComponent(
        title = stringResource(R.string.label_cover),
        summary = status,
        leftAction = {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        },
        rightActions = {
            Text(
                text = stringResource(R.string.editor_cover_pick),
                color = MiuixTheme.colorScheme.primary,
                fontSize = 13.sp,
            )
        },
        onClick = onPickCover,
    )
    if (bitmap != null || state.coverRemoved) {
        BasicComponent(
            title = stringResource(R.string.editor_remove_cover),
            enabled = !state.coverRemoved,
            onClick = onRemoveCover,
        )
    }
}

/** The typed artist picture, offered only where the container stores more than one image. */
@Composable
private fun ArtistCoverRow(
    state: EditorUiState,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    val bitmap = remember(state.artistCoverBytes) {
        state.artistCoverBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    BasicComponent(
        title = stringResource(R.string.editor_artist_cover),
        summary = if (bitmap != null) {
            stringResource(R.string.editor_artist_cover_present)
        } else {
            stringResource(R.string.editor_artist_cover_none)
        },
        leftAction = {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        },
        rightActions = {
            Text(
                text = stringResource(R.string.editor_cover_pick),
                color = MiuixTheme.colorScheme.primary,
                fontSize = 13.sp,
            )
        },
        onClick = onPick,
    )
    if (bitmap != null) {
        BasicComponent(
            title = stringResource(R.string.editor_remove_artist_cover),
            onClick = onRemove,
        )
    }
}

@Composable
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    maxLines: Int = 1,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
