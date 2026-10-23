/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xjcheng.musictageditor.ui.M0Screen
import com.xjcheng.musictageditor.ui.describeIntent
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme

/**
 * Shared shell for screens that are still placeholders.
 *
 * Keeping every screen as a real, registered Activity (with the historical class
 * name) is part of the compatibility contract: callers that target a component
 * explicitly must still resolve. Constructor parameters have defaults so the
 * concrete subclasses get the no-arg constructor Android requires.
 */
open class PlaceholderActivity(
    private val screenTitle: String = "Music Tag",
    private val milestone: String = "",
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val lines = describeIntent(intent)
        setContent {
            MusicTagTheme {
                M0Screen(
                    title = screenTitle,
                    milestone = milestone,
                    lines = lines,
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        trace(screenTitle, intent)
    }

    companion object {
        private const val TAG = "MusicTagCompat"

        /** Logs the incoming intent so `adb logcat` is enough to verify a caller. */
        fun trace(screen: String, intent: Intent?) {
            if (intent == null) return
            Log.i(TAG, "$screen action=${intent.action} type=${intent.type} data=${intent.data}")
        }
    }
}
