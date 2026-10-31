/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.editor

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.Save
import top.yukonga.miuix.kmp.icon.icons.useful.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The editable text fields, in the order the editor shows them. */
enum class TagField(@StringRes val labelRes: Int) {
    TITLE(R.string.label_title),
    ARTIST(R.string.label_artist),
    ALBUM(R.string.label_album),
    ALBUM_ARTIST(R.string.label_album_artist),
    GENRE(R.string.label_genre),
    COMPOSER(R.string.label_composer),
    TRACK(R.string.label_track),
    DISC(R.string.label_disc),
    YEAR(R.string.label_year),
    COMMENT(R.string.label_comment),
    ;

    fun read(edit: TagEdit): String = when (this) {
        TITLE -> edit.title
        ARTIST -> edit.artist
        ALBUM -> edit.album
        ALBUM_ARTIST -> edit.albumArtist
        GENRE -> edit.genre
        COMPOSER -> edit.composer
        TRACK -> edit.track
        DISC -> edit.disc
        YEAR -> edit.year
        COMMENT -> edit.comment
    }

    fun write(edit: TagEdit, value: String): TagEdit = when (this) {
        TITLE -> edit.copy(title = value)
        ARTIST -> edit.copy(artist = value)
        ALBUM -> edit.copy(album = value)
        ALBUM_ARTIST -> edit.copy(albumArtist = value)
        GENRE -> edit.copy(genre = value)
        COMPOSER -> edit.copy(composer = value)
        TRACK -> edit.copy(track = value)
        DISC -> edit.copy(disc = value)
        YEAR -> edit.copy(year = value)
        COMMENT -> edit.copy(comment = value)
    }
}

/** What the editor renders. */
data class EditorUiState(
    val documentName: String = "",
    val container: String = "",
    val tagFormat: String = "",
    val audio: String = "",
    val edit: TagEdit = TagEdit(),
    val original: TagData? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
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
)

/**
 * Tag editor.
 *
 * Reads like a form rather than a list of labels: the artwork card carries the song's identity
 * and its two picture slots, every field is a filled rounded box, and the actions that matter
 * (save, online lookup) sit where the thumb reaches them.
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
    onBack: () -> Unit = {},
) {
    var renameText by rememberSaveable(state.documentName) { mutableStateOf(state.documentName) }
    var confirmDelete by remember { mutableStateOf(false) }
    var slot by remember { mutableStateOf(PictureSlot.COVER) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = state.edit.title.ifBlank {
                    state.documentName.ifBlank { stringResource(R.string.editor_title_fallback) }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Useful.Back, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (state.saving) {
                                    MiuixTheme.colorScheme.secondaryContainer
                                } else {
                                    MiuixTheme.colorScheme.primary
                                },
                            )
                            .clickable(enabled = !state.saving, onClick = onSave)
                            .padding(horizontal = 14.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.action_save),
                            color = if (state.saving) {
                                MiuixTheme.colorScheme.onSurfaceContainerVariant
                            } else {
                                Color.White
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PictureHeader(
                    state = state,
                    slot = slot,
                    onSlotChange = { slot = it },
                    onPickCover = onPickCover,
                    onPickArtistCover = onPickArtistCover,
                    onRemove = {
                        if (slot == PictureSlot.COVER) onRemoveCover() else onRemoveArtistCover()
                    },
                )

                Text(
                    text = metaLine(state),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                state.error?.let { message ->
                    Text(
                        text = message,
                        color = MiuixTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                state.message?.let { message ->
                    Text(
                        text = message,
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                if (state.loading) {
                    Text(
                        text = stringResource(R.string.editor_loading),
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                } else {
                    if (!state.supportsTypedPictures) {
                        Text(
                            text = stringResource(
                                R.string.editor_picture_single_container,
                                state.container,
                            ),
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    for (field in TagField.entries) {
                        FieldCard(
                            labelRes = field.labelRes,
                            value = field.read(state.edit),
                            onValueChange = { onFieldChange(field, it) },
                        )
                    }
                    FieldCard(
                        labelRes = R.string.label_lyrics,
                        value = state.edit.lyrics,
                        onValueChange = onLyricsChange,
                        multiline = true,
                        summary = stringResource(
                            R.string.editor_lyrics_lines,
                            state.edit.lyrics.lines().count { it.isNotBlank() },
                        ),
                    )

                    if (state.isMp3) {
                        BasicComponent(
                            title = stringResource(R.string.editor_write_id3v1),
                            rightActions = {
                                Switch(
                                    checked = state.writeId3v1,
                                    onCheckedChange = onWriteId3v1Change,
                                )
                            },
                        )
                        BasicComponent(
                            title = stringResource(R.string.editor_use_id3v23),
                            rightActions = {
                                Switch(checked = state.id3v23, onCheckedChange = onId3v23Change)
                            },
                        )
                    }

                    SectionTitle(stringResource(R.string.editor_file_section))
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
                    BasicComponent(
                        title = stringResource(R.string.action_share),
                        onClick = onShare,
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

            // The online lookup is the one action worth a permanent, thumb-sized target.
            if (!state.loading) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 96.dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary)
                        .clickable(onClick = onSearchOnline),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        MiuixIcons.Useful.Search,
                        contentDescription = stringResource(R.string.action_search_online),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

private enum class PictureSlot { COVER, ARTIST }

@Composable
private fun PictureHeader(
    state: EditorUiState,
    slot: PictureSlot,
    onSlotChange: (PictureSlot) -> Unit,
    onPickCover: () -> Unit,
    onPickArtistCover: () -> Unit,
    onRemove: () -> Unit,
) {
    val bytes = if (slot == PictureSlot.COVER) state.coverBytes else state.artistCoverBytes
    val bitmap = remember(bytes) {
        bytes?.let { data ->
            runCatching { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }
                .getOrNull()
        }
    }
    val size = remember(bytes) {
        bytes?.let { data ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else {
                null
            }
        }
    }
    val pick = if (slot == PictureSlot.COVER) onPickCover else onPickArtistCover

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer)
                .clickable(onClick = pick),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = stringResource(
                        if (slot == PictureSlot.COVER) R.string.label_cover
                        else R.string.editor_artist_cover,
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = state.edit.title.ifBlank {
                    state.edit.artist.ifBlank { state.documentName }
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (state.edit.artist.isNotBlank()) {
                Text(
                    text = state.edit.artist,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlotChip(
                    text = stringResource(R.string.label_cover),
                    selected = slot == PictureSlot.COVER,
                    onClick = { onSlotChange(PictureSlot.COVER) },
                )
                if (state.supportsTypedPictures) {
                    SlotChip(
                        text = stringResource(R.string.editor_picture_artist_short),
                        selected = slot == PictureSlot.ARTIST,
                        onClick = { onSlotChange(PictureSlot.ARTIST) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (size != null) Chip("${size.first}×${size.second}")
                Chip(text = stringResource(R.string.editor_edit_picture), onClick = pick)
                if (bytes != null) {
                    Chip(text = stringResource(R.string.editor_remove_cover), onClick = onRemove)
                }
            }
        }
    }
}

@Composable
private fun SlotChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.secondaryContainer
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (selected) {
                MiuixTheme.colorScheme.surface
            } else {
                MiuixTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun Chip(text: String, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text, fontSize = 10.sp, color = Color.White)
    }
}

/** One filled field box: small label on top, the value underneath. */
@Composable
private fun FieldCard(
    @StringRes labelRes: Int,
    value: String,
    onValueChange: (String) -> Unit,
    multiline: Boolean = false,
    summary: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(labelRes),
                fontSize = 11.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
            if (summary != null) {
                Text(
                    text = summary,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = !multiline,
            maxLines = if (multiline) 8 else 1,
            textStyle = TextStyle(
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 16.sp,
            ),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp),
    )
}

@Composable
private fun InlineField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 14.sp,
        ),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** "FLAC · VorbisComment · 251s · 44100 Hz · 2 ch · 24 bit" */
private fun metaLine(state: EditorUiState): String = buildList {
    if (state.container.isNotBlank()) add(state.container)
    if (state.tagFormat.isNotBlank() && state.tagFormat != "unknown") add(state.tagFormat)
    if (state.audio.isNotBlank()) add(state.audio)
}.joinToString(" · ")
