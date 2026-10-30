/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.media.MediaTrack
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Edit
import top.yukonga.miuix.kmp.icon.icons.useful.Personal
import top.yukonga.miuix.kmp.icon.icons.useful.Play
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import top.yukonga.miuix.kmp.icon.icons.useful.Stick
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** What the library screen renders. */
data class LibraryUiState(
    val tracks: List<MediaTrack> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
    val message: String? = null,
)

/**
 * The local library.
 *
 * Miuix (MIUI/HyperOS) layout: the title carries the track count, the gear opens the
 * settings, the two trailing icons refresh and start a batch edit, and the bottom bar
 * switches between the song, artist and album views. The row itself is still the plain
 * `BasicComponent` here; the artwork/bitrate/format row from the reference design lands
 * with the library data model.
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
    selectedTab: Int = TAB_SONGS,
    onTabSelected: (Int) -> Unit = {},
) {
    val navItems = listOf(
        NavigationItem(stringResource(R.string.tab_songs), MiuixIcons.Useful.Play),
        NavigationItem(stringResource(R.string.tab_artists), MiuixIcons.Useful.Personal),
        NavigationItem(stringResource(R.string.tab_albums), MiuixIcons.Useful.Stick),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.song_list_title, state.tracks.size),
                navigationIcon = {
                    IconButton(onClick = onSettings) {
                        Icon(MiuixIcons.Useful.Settings, stringResource(R.string.action_settings))
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(MiuixIcons.Useful.Refresh, stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = onBatchEdit, enabled = state.tracks.isNotEmpty()) {
                        Icon(MiuixIcons.Useful.Edit, stringResource(R.string.action_batch_edit))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                items = navItems,
                selected = selectedTab,
                onClick = onTabSelected,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (selectedTab != TAB_SONGS) {
                PendingTab(selectedTab)
                return@Column
            }
            state.message?.let { message ->
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (!state.permissionGranted) {
                PermissionPrompt(onRequestPermission)
                return@Column
            }
            when {
                state.loading -> Hint(stringResource(R.string.library_loading))
                state.tracks.isEmpty() -> Hint(stringResource(R.string.library_empty))
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.tracks, key = { it.id }) { track ->
                        TrackRow(track = track, onOpenTrack = onOpenTrack)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: MediaTrack, onOpenTrack: (MediaTrack) -> Unit) {
    BasicComponent(
        title = track.title.ifBlank { stringResource(R.string.label_unknown_title) },
        summary = subtitle(track),
        rightActions = {
            Text(
                text = formatDuration(track.durationMs),
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                fontSize = 12.sp,
            )
        },
        onClick = { onOpenTrack(track) },
    )
}

@Composable
private fun subtitle(track: MediaTrack): String {
    val artist = track.artist.ifBlank { stringResource(R.string.label_unknown_artist) }
    val album = track.album
    return if (album.isBlank()) artist else "$artist - $album"
}

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.library_permission_reason),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        BasicComponent(
            title = stringResource(R.string.action_grant),
            onClick = onRequestPermission,
        )
    }
}

@Composable
private fun PendingTab(selectedTab: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(
                if (selectedTab == TAB_ARTISTS) R.string.tab_artists_pending else R.string.tab_albums_pending,
            ),
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = text, color = MiuixTheme.colorScheme.onSurfaceContainerVariant, fontSize = 14.sp)
    }
}

/** `m:ss` for a duration, which is enough for a library row. */
fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

const val TAB_SONGS = 0
const val TAB_ARTISTS = 1
const val TAB_ALBUMS = 2
