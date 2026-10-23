/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * User preferences.
 *
 * The original app kept these in a single SharedPreferences file; the keys below are new
 * (the old ones are not carried over, see docs/COMPAT.md), but the file name is the same
 * so a future import of legacy settings has somewhere to land.
 */
data class Settings(
    /** MP3: also write an ID3v1 tag alongside ID3v2. */
    val writeId3v1: Boolean = false,
    /** MP3: write ID3v2.3 instead of ID3v2.4. */
    val id3v23: Boolean = false,
    /** DFF/DSF: write the ID3 chunk (M3). */
    val dffId3: Boolean = true,
)

class SettingsRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        writeId3v1 = preferences.getBoolean(KEY_ID3V1, false),
        id3v23 = preferences.getBoolean(KEY_ID3V23, false),
        dffId3 = preferences.getBoolean(KEY_DFF_ID3, true),
    )

    fun save(settings: Settings) {
        preferences.edit()
            .putBoolean(KEY_ID3V1, settings.writeId3v1)
            .putBoolean(KEY_ID3V23, settings.id3v23)
            .putBoolean(KEY_DFF_ID3, settings.dffId3)
            .apply()
    }

    private companion object {
        const val FILE = "musictag_settings"
        const val KEY_ID3V1 = "write_id3v1"
        const val KEY_ID3V23 = "write_id3v23"
        const val KEY_DFF_ID3 = "write_dff_id3"
    }
}
