/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.app.Application
import com.xjcheng.musictageditor.logging.AppLog
import com.xjcheng.musictageditor.settings.Appearance

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
        AppLog.init(this)
        Appearance.refresh(this)
    }
