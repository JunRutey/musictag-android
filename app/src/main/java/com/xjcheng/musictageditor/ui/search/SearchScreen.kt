/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.search

import androidx.compose.foundation.clickable
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
import com.xjcheng.musictageditor.core.net.SearchResult
import com.xjcheng.musictageditor.core.net.TagCandidate

/** What the search screen renders. */
data class SearchUiState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val searching: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val selected: TagCandidate? = null,
    val selectedLabel: String? = null,
    val autoMatched: Boolean = false,
    val error: String? = null,
    val failures: List<String> = emptyList(),
)

@Composable
fun SearchScreen(
    state: SearchUiState,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
    onSearch: () -> Unit,
    onAutoMatch: () -> Unit,
    onSelectResult: (SearchResult) -> Unit,
    onUseCandidate: () -> Unit,
    onDismissCandidate: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Find tags online", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = state.artist,
                    onValueChange = onArtistChange,
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = state.album,
                    onValueChange = onAlbumChange,
                    label = { Text("Album (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onSearch, enabled = !state.searching && state.title.isNotBlank()) {
                        Text("Search")
                    }
                    OutlinedButton(
                        onClick = onAutoMatch,
                        enabled = !state.searching && state.title.isNotBlank(),
                    ) {
                        Text("Auto match all sources")
                    }
                    if (state.searching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    }
                }
                state.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (state.failures.isNotEmpty()) {
                    Text(
                        text = "Some sources did not answer: ${state.failures.joinToString()}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            HorizontalDivider()

            val selected = state.selected
            if (selected != null) {
                CandidatePreview(selected, state.selectedLabel, onUseCandidate, onDismissCandidate)
            } else {
                Results(state, onSelectResult)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun Results(state: SearchUiState, onSelect: (SearchResult) -> Unit) {
    if (state.results.isEmpty()) {
        Text(
            text = if (state.searching) "Searching…" else "No results yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(state.results) { result ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.searching) { onSelect(result) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(result.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = listOf(result.artist, result.album).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = result.provider.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun CandidatePreview(
    candidate: TagCandidate,
    label: String?,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            Text(
                text = label?.let { "Match from $it" } ?: "Match",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        val rows = listOf(
            "Title" to candidate.title,
            "Artist" to candidate.artist,
            "Album" to candidate.album,
            "Album artist" to candidate.albumArtist,
            "Genre" to candidate.genre,
            "Composer" to candidate.composer,
            "Track" to candidate.track,
            "Disc" to candidate.disc,
            "Year" to candidate.year,
            "Comment" to candidate.comment,
            "Lyrics" to candidate.lyrics?.let { "${it.length} characters" },
            "Cover" to candidate.coverUrl,
        ).filter { it.second != null }
        items(rows.size) { index ->
            val (name, value) = rows[index]
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp),
                )
                Text(text = value.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onUse) { Text("Use these tags") }
                OutlinedButton(onClick = onDismiss) { Text("Back to results") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
