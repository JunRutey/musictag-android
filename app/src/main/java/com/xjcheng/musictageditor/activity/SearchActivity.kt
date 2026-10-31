/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

import android.app.Activity
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.core.net.SearchQuery
import com.xjcheng.musictageditor.core.net.SearchResult
import com.xjcheng.musictageditor.net.Providers
import com.xjcheng.musictageditor.net.SearchContract
import com.xjcheng.musictageditor.settings.SettingsRepository
import com.xjcheng.musictageditor.ui.search.SearchScreen
import com.xjcheng.musictageditor.ui.search.SearchUiState
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Online tag search.
 *
 * Two ways in: the editor starts it for a specific file (title/artist prefilled, result
 * handed back), or the system search intent opens it with a free-text query. Either way
 * the screen queries the providers, lists the rows with their source, and returns the
 * chosen candidate as flat extras (see [SearchContract]).
 */
class SearchActivity : ComponentActivity() {

    private val settings by lazy { SettingsRepository(this) }
    private val registry by lazy { Providers.registry(settings.load()) }

    private var ui by mutableStateOf(SearchUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ui = ui.copy(
            title = intent?.getStringExtra(SearchContract.EXTRA_TITLE)
                ?: intent?.getStringExtra(SearchManager.QUERY).orEmpty(),
            artist = intent?.getStringExtra(SearchContract.EXTRA_ARTIST).orEmpty(),
            album = intent?.getStringExtra(SearchContract.EXTRA_ALBUM).orEmpty(),
        )

        // Opened from the editor with a title already known: search immediately, so the
        // only decision left is which candidate to pick.
        if (ui.title.isNotBlank()) {
            search()
        }

        setContent {
            MusicTagTheme {
                SearchScreen(
                    state = ui,
                    onTitleChange = { ui = ui.copy(title = it) },
                    onArtistChange = { ui = ui.copy(artist = it) },
                    onAlbumChange = { ui = ui.copy(album = it) },
                    onSearch = ::search,
                    onAutoMatch = ::autoMatch,
                    onSelectResult = ::select,
                    onUseCandidate = ::useCandidate,
                    onDismissCandidate = { ui = ui.copy(selected = null, selectedLabel = null) },
                    onCancel = { finish() },
                )
            }
        }
    }

    private fun query(): SearchQuery = SearchQuery(
        title = ui.title.trim(),
        artist = ui.artist.trim(),
        album = ui.album.trim(),
    )

    private fun search() {
        AppLog.d("Search", "search title='${ui.title}' artist='${ui.artist}' album='${ui.album}'")
        lifecycleScope.launch {
            ui = ui.copy(searching = true, error = null, results = emptyList(), selected = null)
            registry.clearFailures()
            val startedAt = System.currentTimeMillis()
            val rows = withContext(Dispatchers.IO) {
                runCatching { registry.searchAll(query(), limitPerProvider = 10) }
                    .getOrDefault(emptyList())
            }
            val grouped = rows.groupingBy { it.provider.key }.eachCount()
            AppLog.d(
                "Search",
                "search done in ${System.currentTimeMillis() - startedAt}ms: " +
                    "results=${rows.size} perProvider=$grouped " +
                    "failures=${registry.failures().map { it.provider.key }}",
            )
            ui = ui.copy(
                searching = false,
                results = rows,
                failures = registry.failures().map { it.provider.displayName },
                error = if (rows.isEmpty()) getString(R.string.search_error_no_result) else null,
            )
        }
    }

    private fun autoMatch() {
        lifecycleScope.launch {
            ui = ui.copy(searching = true, error = null, results = emptyList(), selected = null)
            registry.clearFailures()
            val candidate = withContext(Dispatchers.IO) {
                runCatching { registry.autoMatch(query(), limitPerProvider = 5) }.getOrNull()
            }
            ui = if (candidate == null || candidate.isEmpty()) {
                ui.copy(
                    searching = false,
                    failures = registry.failures().map { it.provider.displayName },
                    error = getString(R.string.search_error_no_match),
                )
            } else {
                ui.copy(
                    searching = false,
                    selected = candidate,
                    selectedLabel = getString(R.string.search_all_sources),
                    autoMatched = true,
                    failures = registry.failures().map { it.provider.displayName },
                )
            }
        }
    }

    private fun select(result: SearchResult) {
        AppLog.d("Search", "detail ${result.provider.key}:${result.id}")
        lifecycleScope.launch {
            ui = ui.copy(searching = true, error = null)
            val candidate = withContext(Dispatchers.IO) {
                runCatching { registry.detailFor(result) }.getOrNull()
            }
            ui = if (candidate == null || candidate.isEmpty()) {
                ui.copy(searching = false, error = getString(R.string.search_error_source_empty, result.provider.displayName))
            } else {
                ui.copy(
                    searching = false,
                    selected = candidate,
                    selectedLabel = result.provider.displayName,
                    autoMatched = false,
                )
            }
        }
    }

    private fun useCandidate() {
        val candidate = ui.selected ?: return
        setResult(Activity.RESULT_OK, SearchContract.write(candidate))
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val query = intent.getStringExtra(SearchManager.QUERY) ?: return
        ui = ui.copy(title = query)
        search()
    }
}
