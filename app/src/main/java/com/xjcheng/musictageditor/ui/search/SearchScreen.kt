/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.search

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import com.xjcheng.musictageditor.io.CoverDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.core.net.SearchResult
import com.xjcheng.musictageditor.core.net.TagCandidate
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

/**
 * Online tag search, in Miuix.
 *
 * The three query fields are always visible; the results list shows one row per hit with
 * its source, and selecting a row previews every field the source returned before the user
 * decides to apply it.
 */
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.search_title),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(MiuixIcons.Useful.Back, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            QueryFields(
                state = state,
                onTitleChange = onTitleChange,
                onArtistChange = onArtistChange,
                onAlbumChange = onAlbumChange,
            )
            BasicComponent(
                title = stringResource(R.string.action_search),
                enabled = !state.searching && state.title.isNotBlank(),
                onClick = onSearch,
            )
            BasicComponent(
                title = stringResource(R.string.action_auto_match),
                enabled = !state.searching && state.title.isNotBlank(),
                onClick = onAutoMatch,
            )
            state.error?.let { message ->
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (state.failures.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.search_failures, state.failures.joinToString("、")),
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val selected = state.selected
            if (selected != null) {
                CandidatePreview(
                    candidate = selected,
                    label = state.selectedLabel,
                    onUse = onUseCandidate,
                    onDismiss = onDismissCandidate,
                )
            } else {
                Results(state = state, onSelect = onSelectResult)
            }
        }
    }
}

@Composable
private fun QueryFields(
    state: SearchUiState,
    onTitleChange: (String) -> Unit,
    onArtistChange: (String) -> Unit,
    onAlbumChange: (String) -> Unit,
) {
    FieldRow(
        label = stringResource(R.string.label_title),
        value = state.title,
        onValueChange = onTitleChange,
    )
    FieldRow(
        label = stringResource(R.string.label_artist),
        value = state.artist,
        onValueChange = onArtistChange,
    )
    FieldRow(
        label = stringResource(R.string.search_field_album),
        value = state.album,
        onValueChange = onAlbumChange,
    )
}

@Composable
private fun FieldRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            fontSize = 14.sp,
            modifier = Modifier.width(96.dp),
        )
        // Same deeper input surface as the settings and the editor.
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 14.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Results(state: SearchUiState, onSelect: (SearchResult) -> Unit) {
    if (state.results.isEmpty()) {
        Text(
            text = stringResource(if (state.searching) R.string.search_searching else R.string.search_no_results),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(state.results, key = { "${it.provider.key}:${it.id}" }) { result ->
            BasicComponent(
                title = result.title,
                summary = listOf(result.artist, result.album)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                leftAction = { ResultCover(url = result.coverUrl) },
                rightActions = {
                    Text(
                        text = result.provider.displayName,
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                enabled = !state.searching,
                onClick = { onSelect(result) },
            )
        }
    }
}

/** 44dp cover thumbnail for one search hit, cached by URL. */
@Composable
private fun ResultCover(url: String?) {
    if (url.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MiuixTheme.colorScheme.secondaryContainer),
        )
        return
    }
    var image by remember(url) { mutableStateOf(SearchCoverCache.get(url)) }
    LaunchedEffect(url) {
        if (image == null) {
            image = SearchCoverCache.load(url)
        }
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** Small LRU cache so scrolling the result list does not refetch every cover. */
private object SearchCoverCache {

    private const val MAX_ENTRIES = 60
    private val cache = object : LruCache<String, ImageBitmap>(MAX_ENTRIES) {}

    fun get(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val bytes = withContext(Dispatchers.IO) {
            runCatching { CoverDownloader().fetch(url) }.getOrNull()
        } ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        } ?: return null
        cache.put(url, bitmap)
        return bitmap
    }
}

@Composable
private fun CandidatePreview(
    candidate: TagCandidate,
    label: String?,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = listOf(
        stringResource(R.string.label_title) to candidate.title,
        stringResource(R.string.label_artist) to candidate.artist,
        stringResource(R.string.label_album) to candidate.album,
        stringResource(R.string.label_album_artist) to candidate.albumArtist,
        stringResource(R.string.label_genre) to candidate.genre,
        stringResource(R.string.label_composer) to candidate.composer,
        stringResource(R.string.label_track) to candidate.track,
        stringResource(R.string.label_disc) to candidate.disc,
        stringResource(R.string.label_year) to candidate.year,
        stringResource(R.string.label_comment) to candidate.comment,
        stringResource(R.string.label_lyrics) to candidate.lyrics?.let {
            stringResource(R.string.search_lyrics_length, it.length)
        },
        stringResource(R.string.label_cover) to candidate.coverUrl,
    ).filter { it.second != null }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            Text(
                text = label?.let { stringResource(R.string.search_match_from, it) }
                    ?: stringResource(R.string.search_match),
                color = MiuixTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(rows.size) { index ->
            val (name, value) = rows[index]
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                Text(
                    text = name,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.width(96.dp),
                )
                Text(text = value.orEmpty(), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicComponent(title = stringResource(R.string.search_use_tags), onClick = onUse)
                BasicComponent(title = stringResource(R.string.search_back_to_results), onClick = onDismiss)
            }
        }
    }
}
