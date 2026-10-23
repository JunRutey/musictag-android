/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xjcheng.musictageditor.activity.PlaceholderActivity
import com.xjcheng.musictageditor.ui.M0Screen
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme

/**
 * Launcher screen.
 *
 * M0 only proves the app builds, installs, launches and owns the compatibility
 * surface. Library browsing lands in M2, online providers in M4.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MusicTagTheme {
                M0Screen(
                    title = "Music Tag",
                    milestone = "M0 scaffold · version ${BuildConfig.VERSION_NAME}",
                    lines = listOf(
                        "package: $packageName",
                        "library browsing → M2",
                        "tag engine (TagLib/JNI) → M1",
                        "online providers → M4",
                        "batch service → M5",
                        "",
                        "Compatibility surface is live: open an audio file from another",
                        "app to see this build receive it in SongDetailActivity.",
                    ),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop: a caller re-launching with a document URI is forwarded here.
        // Routing to SongDetailActivity lands in M2.
        PlaceholderActivity.trace("MainActivity.onNewIntent", intent)
    }
}
