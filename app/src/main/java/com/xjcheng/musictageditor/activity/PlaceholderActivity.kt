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
import androidx.annotation.StringRes
import com.xjcheng.musictageditor.R
import com.xjcheng.musictageditor.ui.M0Screen
import com.xjcheng.musictageditor.ui.describeIntent
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme

/**
 * Shared shell for screens that are still placeholders.
 *
 * Keeping every screen as a real, registered Activity (with the historical class name) is
 * part of the compatibility contract: callers that target a component explicitly must still
 * resolve. Titles and notes are resource ids, so the shell is Chinese by default and the
 * subclasses keep the no-arg constructor Android requires.
 */
open class PlaceholderActivity(
    @StringRes private val screenTitleRes: Int = R.string.app_name,
    @StringRes private val noteRes: Int = 0,
) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val lines = describeIntent(intent)
        val title = getString(screenTitleRes)
        val note = if (noteRes != 0) getString(noteRes) else ""
        setContent {
            MusicTagTheme {
                M0Screen(
                    title = title,
                    milestone = note,
                    lines = lines,
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        trace(getString(screenTitleRes), intent)
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
