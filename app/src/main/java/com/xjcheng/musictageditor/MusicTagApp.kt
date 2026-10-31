/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.app.Application
import com.xjcheng.musictageditor.logging.AppLog
import com.xjcheng.musictageditor.settings.Appearance
import com.xjcheng.musictageditor.settings.SettingsRepository

/**
 * Application entry point.
 *
 * Deliberately does no work in M0: no global mutable state, no eager I/O.
 * Long-lived collaborators (settings repository, media index, provider registry)
 * are created lazily by the modules that own them.
 */
class MusicTagApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Logging and the theme preference are needed by every entry point, including the
        // ones other apps start directly (VIEW / SEND), so they are initialised here.
        runCatching { AppLog.init(this) }
        // A corrupted or old-format preference file must never stop the app from starting.
        runCatching {
            AppLog.setVerbose(SettingsRepository(this).load().verboseLogging)
            Appearance.refresh(this)
        }.onFailure { AppLog.w("App", "startup preference read failed", it) }
    }
}
