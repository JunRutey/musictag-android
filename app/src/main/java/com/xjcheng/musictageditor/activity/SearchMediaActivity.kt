/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
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
import com.xjcheng.musictageditor.logging.AppLog
import com.xjcheng.musictageditor.media.MediaTrack
import com.xjcheng.musictageditor.media.Permissions
import com.xjcheng.musictageditor.settings.SettingsRepository
import com.xjcheng.musictageditor.ui.library.LibraryScreen
import com.xjcheng.musictageditor.ui.library.LibraryUiState
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Local media search (`SEARCH`) and the library list.
 *
 * The whole library is loaded once, then filtered in memory by the settings rules
 * (minimum duration, ignored directories, MP4 videos) and by the query - see
 * [MediaFilters], which is unit tested. Tapping a row opens the editor on that URI.
 */
open class SearchMediaActivity : ComponentActivity() {

    private val settings by lazy { SettingsRepository(this) }
    private val library by lazy { MediaLibrary(this) }

    private var ui by mutableStateOf(LibraryUiState())

    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        ui = ui.copy(permissionGranted = granted)
        if (granted) load() else ui = ui.copy(loading = false, message = getString(R.string.library_permission_denied))
    }

    /**
     * First launch asks for audio read, legacy storage write and notifications together, so a
     * later feature does not have to interrupt the user with another dialog.
     */
    private val requestFirstLaunch =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            AppLog.i("Permissions", "first launch result: $result")
            val granted = Permissions.audioGranted(this)
            ui = ui.copy(permissionGranted = granted)
            if (granted) load() else ui = ui.copy(loading = false)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = ui.copy(
            query = intent?.getStringExtra(SearchManager.QUERY).orEmpty(),
            permissionGranted = hasAudioPermission(),
        )
        if (!Permissions.askedBefore(this)) {
            Permissions.markAsked(this)
            AppLog.i("Permissions", "requesting ${Permissions.firstLaunch()}")
            requestFirstLaunch.launch(Permissions.firstLaunch().toTypedArray())
        }
        setContent {
            MusicTagTheme {
                LibraryScreen(
                    state = ui,
                    onQueryChange = ::updateQuery,
                    onRefresh = { load() },
                    onRequestPermission = { requestPermission.launch(audioPermission()) },
                    onOpenTrack = ::openTrack,
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onTabSelected = { tab -> ui = ui.copy(selectedTab = tab) },
                )
            }
        }
        if (ui.permissionGranted) load() else ui = ui.copy(loading = false)
    }

    private var libraryTracks: List<MediaTrack> = emptyList()
    private var hasLoaded = false
    private var loadJob: Job? = null
    private var queryJob: Job? = null

    private fun load() {
        loadJob?.cancel()
        queryJob?.cancel()
        loadJob = lifecycleScope.launch {
            ui = ui.copy(loading = true, message = null)
            try {
                val (loaded, filterSettings) = coroutineScope {
                    val tracks = async(Dispatchers.IO) { library.query() }
                    val preferences = async(Dispatchers.IO) { settings.load() }
                    tracks.await() to preferences.await()
                }
                val filtered = withContext(Dispatchers.Default) {
                    MediaFilters.apply(
                        tracks = loaded,
                        minDurationSeconds = filterSettings.ignoreDurationSeconds,
                        ignoredDirectories = filterSettings.ignoreDirectories,
                        ignoreMp4Videos = filterSettings.ignoreMp4Videos,
                    )
                }
                libraryTracks = filtered
                hasLoaded = true
                val query = ui.query
                val visible = withContext(Dispatchers.Default) {
                    filtered.filter { MediaFilters.matchesQuery(it, query) }
                }
                ui = ui.copy(
                    loading = false,
                    message = null,
                    libraryTracks = filtered,
                    tracks = visible,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AppLog.e("Library", "failed to load media library", error)
                ui = ui.copy(loading = false, message = getString(R.string.library_error))
            }
        }
    }

    private fun updateQuery(query: String) {
        ui = ui.copy(query = query)
        queryJob?.cancel()
        queryJob = lifecycleScope.launch {
            delay(120)
            val matches = withContext(Dispatchers.Default) {
                libraryTracks.filter { MediaFilters.matchesQuery(it, query) }
            }
            if (ui.query == query) ui = ui.copy(tracks = matches)
        }
    }

    private fun openTrack(track: MediaTrack) {
        AppLog.i("Library", "open ${track.title} (${track.uri})")
        // Hand the URI over as a plain extra. Putting it in data with FLAG_GRANT_* makes the
        // system verify that *we* can grant access to it; for a MediaStore row we cannot, so
        // startActivity itself threw SecurityException on Android 14+. The editor reads the
        // URI with its own media permission, so no grant is needed.
        startActivity(
            Intent(this, SongDetailActivity::class.java)
                .putExtra(SongDetailActivity.EXTRA_URI, track.uri),
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
        updateQuery(query)
        if (!hasLoaded) load()
    }
}
