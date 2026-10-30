/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.SongDetailActivity
import com.xjcheng.musictageditor.media.MediaFilters
import com.xjcheng.musictageditor.media.MediaLibrary
import com.xjcheng.musictageditor.media.MediaTrack
import com.xjcheng.musictageditor.settings.SettingsRepository
import com.xjcheng.musictageditor.ui.library.LibraryScreen
import com.xjcheng.musictageditor.ui.library.LibraryUiState
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local media search (`SEARCH`) and the library list.
 *
 * The whole library is loaded once, then filtered in memory by the settings rules
 * (minimum duration, ignored directories, MP4 videos) and by the query - see
 * [MediaFilters], which is unit tested. Tapping a row opens the editor on that URI.
 */
class SearchMediaActivity : ComponentActivity() {

    private val settings by lazy { SettingsRepository(this) }
    private val library by lazy { MediaLibrary(this) }

    private var ui by mutableStateOf(LibraryUiState())

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ui = ui.copy(permissionGranted = granted)
        if (granted) load() else ui = ui.copy(loading = false, message = getString(R.string.library_permission_denied))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ui.copy(
            query = intent?.getStringExtra(SearchManager.QUERY).orEmpty(),
            permissionGranted = hasAudioPermission(),
        )
        setContent {
            MusicTagTheme {
                LibraryScreen(
                    state = ui,
                    onQueryChange = { query -> ui = ui.copy(query = query); applyFilter(loadRaw = false) },
                    onRefresh = { load() },
                    onRequestPermission = { requestPermission.launch(audioPermission()) },
                    onOpenTrack = ::openTrack,
                    onBatchEdit = ::openBatchEditor,
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onTabSelected = { tab -> ui = ui.copy(selectedTab = tab) },
                )
            }
        }
        if (ui.permissionGranted) load() else ui = ui.copy(loading = false)
    }

    private var allTracks: List<MediaTrack> = emptyList()

    private fun load() {
        lifecycleScope.launch {
            ui = ui.copy(loading = true, message = null)
            val loaded = withContext(Dispatchers.IO) { runCatching { library.query() } }
            allTracks = loaded.getOrDefault(emptyList())
            ui = ui.copy(
                message = if (loaded.isFailure) getString(R.string.library_error) else null,
            )
            applyFilter(loadRaw = true)
        }
    }

    private fun applyFilter(loadRaw: Boolean) {
        if (loadRaw && allTracks.isEmpty()) {
            ui = ui.copy(loading = false, tracks = emptyList())
            return
        }
        val settings = settings.load()
        val filtered = MediaFilters.apply(
            tracks = allTracks,
            minDurationSeconds = settings.ignoreDurationSeconds,
            ignoredDirectories = settings.ignoreDirectories,
            ignoreMp4Videos = settings.ignoreMp4Videos,
            query = ui.query,
        )
        ui = ui.copy(loading = false, tracks = filtered)
    }

    private fun openBatchEditor() {
        startActivity(
            Intent(this, BatchEditSongsDetail::class.java)
                .putStringArrayListExtra(
                    BatchEditSongsDetail.EXTRA_URIS,
                    ArrayList(ui.tracks.map { it.uri }),
                ),
        )
    }

    private fun openTrack(track: MediaTrack) {
        startActivity(
            Intent(this, SongDetailActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse(track.uri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        )
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            @Suppress("DEPRECATION")
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val query = intent.getStringExtra(SearchManager.QUERY) ?: return
        ui = ui.copy(query = query)
        if (allTracks.isEmpty()) load() else applyFilter(loadRaw = false)
    }
}
