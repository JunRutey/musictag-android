/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xjcheng.musictageditor.media.MediaTrack

/** What the library screen renders. */
data class LibraryUiState(
    val tracks: List<MediaTrack> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
    val message: String? = null,
)

/**
 * The local library: every track MediaStore knows about, filtered by the settings and by a
 * free-text query. Tapping a row opens the editor for that `content://` URI.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenTrack: (MediaTrack) -> Unit,
    onBatchEdit: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Library", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRefresh) { Text("Refresh") }
                        OutlinedButton(onClick = onSettings) { Text("Settings") }
                    }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text("Filter by title, artist or album") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp).width(24.dp))
                }
                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (!state.permissionGranted) {
                    Text(
                        text = "Reading the media library needs access to audio files.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Allow access")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.tracks.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onBatchEdit, enabled = state.tracks.isNotEmpty()) {
                        Text("Batch edit")
                    }
                }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.tracks, key = { it.id }) { track ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTrack(track) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(track.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ") +
                                "  " + formatDuration(track.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** `m:ss` for a duration, which is enough for a library row. */
fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
