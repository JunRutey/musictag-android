/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * The appearance preference as observable state.
 *
 * `theme_mode` and `dynamic_color` map onto Miuix's own [ColorSchemeMode], so the theme is
 * driven by the library instead of a hand-rolled dark flag: System/Light/Dark plus the three
 * Monet variants that read the wallpaper.
 */
object Appearance {

    var mode by mutableStateOf(ColorSchemeMode.System)
        private set

    fun update(themeMode: String, dynamic: Boolean) {
        mode = modeOf(themeMode, dynamic)
    }

    /** Re-reads the stored preferences; cheap enough to call from an activity's onCreate. */
    fun refresh(context: Context) {
        val settings = SettingsRepository(context.applicationContext).load()
        mode = modeOf(settings.themeMode, settings.dynamicColor)
    }

    fun modeOf(themeMode: String, dynamic: Boolean): ColorSchemeMode = when (themeMode) {
        "light" -> if (dynamic) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        "dark" -> if (dynamic) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
        else -> if (dynamic) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    }
}
