/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.xjcheng.musictageditor.core.tag.TagData
import com.xjcheng.musictageditor.core.tag.TagEdit

/** The twelve editable fields, in the same order as the original editor. */
enum class TagField(
    val label: String,
    val read: (TagEdit) -> String,
    val write: (TagEdit, String) -> TagEdit,
) {
    TITLE("Title", { it.title }, { edit, value -> edit.copy(title = value) }),
    ARTIST("Artist", { it.artist }, { edit, value -> edit.copy(artist = value) }),
    ALBUM("Album", { it.album }, { edit, value -> edit.copy(album = value) }),
    ALBUM_ARTIST("Album artist", { it.albumArtist }, { edit, value -> edit.copy(albumArtist = value) }),
    GENRE("Genre", { it.genre }, { edit, value -> edit.copy(genre = value) }),
    COMPOSER("Composer", { it.composer }, { edit, value -> edit.copy(composer = value) }),
    TRACK("Track (n or n/m)", { it.track }, { edit, value -> edit.copy(track = value) }),
    DISC("Disc (n or n/m)", { it.disc }, { edit, value -> edit.copy(disc = value) }),
    YEAR("Year", { it.year }, { edit, value -> edit.copy(year = value) }),
    COMMENT("Comment", { it.comment }, { edit, value -> edit.copy(comment = value) }),
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
    val isMp3: Boolean = false,
    val writeId3v1: Boolean = false,
    val id3v23: Boolean = false,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@Composable
fun TagEditorScreen(
    state: EditorUiState,
    onFieldChange: (TagField, String) -> Unit,
    onLyricsChange: (String) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onWriteId3v1Change: (Boolean) -> Unit,
    onId3v23Change: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(state)
            HorizontalDivider()
            if (state.loading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }
            Fields(state, onFieldChange, onLyricsChange)
            Cover(state, onPickCover, onRemoveCover)
            if (state.isMp3) {
                Mp3Options(state, onWriteId3v1Change, onId3v23Change)
            }
            SaveBar(state, onSave)
        }
    }
}

@Composable
private fun Header(state: EditorUiState) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = state.documentName.ifBlank { "No document" }, style = MaterialTheme.typography.titleMedium)
        val facts = listOfNotNull(
            state.container.takeIf { it.isNotBlank() },
            state.tagFormat.takeIf { it.isNotBlank() && it != "none" },
            state.audio.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (facts.isNotBlank()) {
            Text(
                text = facts,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun Fields(
    state: EditorUiState,
    onFieldChange: (TagField, String) -> Unit,
    onLyricsChange: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
        items(TagField.entries.toList()) { field ->
            OutlinedTextField(
                value = field.read(state.edit),
                onValueChange = { onFieldChange(field, it) },
                label = { Text(field.label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
        item {
            OutlinedTextField(
                value = state.edit.lyrics,
                onValueChange = onLyricsChange,
                label = { Text("Lyrics") },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Cover(state: EditorUiState, onPickCover: () -> Unit, onRemoveCover: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = state.coverBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(72.dp),
            ) {
                Text("no\ncover", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            OutlinedButton(onClick = onPickCover, enabled = !state.saving) {
                Text(if (state.coverBytes == null) "Add cover" else "Replace cover")
            }
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onRemoveCover,
                enabled = !state.saving && state.coverBytes != null,
            ) {
                Text("Remove cover")
            }
        }
    }
}

@Composable
private fun Mp3Options(
    state: EditorUiState,
    onWriteId3v1Change: (Boolean) -> Unit,
    onId3v23Change: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.writeId3v1, onCheckedChange = onWriteId3v1Change)
            Text("Also write ID3v1")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.id3v23, onCheckedChange = onId3v23Change)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Use ID3v2.3 (older players)")
        }
    }
}

@Composable
private fun SaveBar(state: EditorUiState, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.saving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
        }
        Button(onClick = onSave, enabled = !state.saving && state.original != null) {
            Text("Save tags")
        }
    }
}
