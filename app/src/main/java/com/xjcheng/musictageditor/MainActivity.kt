/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.xjcheng.musictageditor.activity.SearchMediaActivity
import com.xjcheng.musictageditor.logging.AppLog
import com.xjcheng.musictageditor.settings.Appearance

/**
 * Launcher entry point.
 *
 * The original app opened straight into its library, and so does this one: the class stays
 * registered as `MAIN`/`LAUNCHER` (that is part of the compatibility contract) but it hands
 * over to the library screen immediately instead of showing a menu nobody needs.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(this)
        Appearance.refresh(this)
        startActivity(Intent(this, SearchMediaActivity::class.java))
        finish()
    }
}
