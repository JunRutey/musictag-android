/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The appearance preference as observable state.
 *
 * `dark_theme` used to be read once inside `remember`, so flipping the switch could not
 * recompose anything: the value was cached for the lifetime of the activity. Keeping it in a
 * snapshot state makes every screen that reads it update immediately.
 */
object Appearance {

    var darkTheme by mutableStateOf(false)
        private set

    fun update(dark: Boolean) {
        darkTheme = dark
    }

    /** Re-reads the stored preference; call from an activity's onCreate or a toggle. */
    fun refresh(context: Context) {
        darkTheme = SettingsRepository(context.applicationContext).load().darkTheme
    }
}
