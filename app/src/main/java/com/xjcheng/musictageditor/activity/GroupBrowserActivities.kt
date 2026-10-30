/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.media.AlbumGroup
import com.xjcheng.musictageditor.media.ArtistGroup
import com.xjcheng.musictageditor.media.AudioPermission
import com.xjcheng.musictageditor.media.MediaGrouping
import com.xjcheng.musictageditor.media.MediaLibrary
import com.xjcheng.musictageditor.media.MediaTrack
import com.xjcheng.musictageditor.ui.library.GroupBrowserScreen
import com.xjcheng.musictageditor.ui.library.GroupBrowserState
import com.xjcheng.musictageditor.ui.library.GroupRow
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared implementation of the artist and album browsers.
 *
 * Both list the same shape of row; opening one hands the name to the library as a search
 * query, so there is exactly one song list in the app. The subclass only names the rows and
 * supplies the title.
 */
abstract class GroupBrowserActivity : ComponentActivity() {

    private val library by lazy { MediaLibrary(this) }

    private var state by mutableStateOf(GroupBrowserState())

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            state = state.copy(permissionGranted = granted)
            if (granted) load() else state = state.copy(loading = false)
        }

    @get:StringRes
    protected abstract val titleRes: Int

    protected abstract fun rows(tracks: List<MediaTrack>): List<GroupRow>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = state.copy(permissionGranted = AudioPermission.granted(this))
        setContent {
            MusicTagTheme {
                GroupBrowserScreen(
                    title = getString(titleRes),
                    state = state,
                    onBack = { finish() },
                    onRequestPermission = { requestPermission.launch(AudioPermission.name()) },
                    onOpen = ::open,
                )
            }
        }
        if (state.permissionGranted) load() else state = state.copy(loading = false)
    }

    private fun load() {
        lifecycleScope.launch {
            state = state.copy(loading = true)
            val tracks = withContext(Dispatchers.IO) {
                runCatching { library.query() }.getOrDefault(emptyList())
            }
            state = GroupBrowserState(
                rows = rows(tracks),
                loading = false,
                permissionGranted = true,
            )
        }
    }

    private fun open(row: GroupRow) {
        startActivity(
            Intent(this, SearchMediaActivity::class.java)
                .putExtra(SearchManager.QUERY, row.filter),
        )
    }
}

/** The artist browser (`activity.ArtistActivity`, kept for the compatibility contract). */
class ArtistActivity : GroupBrowserActivity() {

    override val titleRes: Int get() = R.string.tab_artists

    override fun rows(tracks: List<MediaTrack>): List<GroupRow> =
        MediaGrouping.artists(tracks).map { artist: ArtistGroup ->
            GroupRow(
                title = artist.name.ifBlank { getString(R.string.label_unknown_artist) },
                summary = getString(R.string.library_count, artist.trackCount),
                filter = artist.name,
            )
        }
}

/** The album browser (`activity.AlbumActivity`, kept for the compatibility contract). */
class AlbumActivity : GroupBrowserActivity() {

    override val titleRes: Int get() = R.string.tab_albums

    override fun rows(tracks: List<MediaTrack>): List<GroupRow> =
        MediaGrouping.albums(tracks).map { album: AlbumGroup ->
            GroupRow(
                title = album.name.ifBlank { getString(R.string.label_unknown_album) },
                summary = listOf(
                    album.artist.ifBlank { getString(R.string.label_unknown_artist) },
                    getString(R.string.library_count, album.trackCount),
                ).joinToString(" · "),
                filter = album.name,
            )
        }
}
