/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.xjcheng.musictageditor.settings.Settings
import com.xjcheng.musictageditor.settings.SettingsRepository
import com.xjcheng.musictageditor.ui.settings.SettingsScreen
import com.xjcheng.musictageditor.ui.theme.MusicTagTheme

/**
 * Settings.
 *
 * Replaces the M0 placeholder while keeping the historical class name and the manifest
 * entry, so explicit-component callers keep working. The whole preference file is written
 * once, when the activity leaves the foreground.
 */
class SettingsActivity : ComponentActivity() {

    private val repository by lazy { SettingsRepository(this) }
    private var settings by mutableStateOf(Settings())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = repository.load()
        setContent {
            MusicTagTheme {
                SettingsScreen(
                    settings = settings,
                    onUpdate = { key, value -> settings = settings.with(key, value) },
                    onReset = { settings = repository.reset() },
                    onBack = { finish() },
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        repository.save(settings)
    }
}
