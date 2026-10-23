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
import androidx.activity.enableEdgeToEdge
import com.xjcheng.musictageditor.ui.M0Screen
import com.xjcheng.musictageditor.ui.describeIntent
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme

/**
 * System-search entry point (`android.intent.action.SEARCH`).
 *
 * Declared with `android.app.searchable` metadata pointing at res/xml/searchable.xml,
 * which also wires the SearchRecentSuggestions authority. Local media search lands in
 * M5; M0 only proves the intent is received and the query is readable.
 */
class SearchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicTagTheme {
                M0Screen(
                    title = "Search",
                    milestone = "M5 — local media search",
                    lines = describeIntent(intent) + listOf(
                        "",
                        "query: ${intent?.getStringExtra(SearchManager.QUERY) ?: "<none>"}",
                    ),
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PlaceholderActivity.trace("SearchActivity", intent)
    }
}
