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
 * Keys and defaults come from [SettingsSchema]; the typed getters below keep call sites
 * readable while storage stays a plain key/value map (which is what SharedPreferences is).
 * The original app used the same file name, so a legacy import has a landing place.
 */
data class Settings(val values: Map<String, Any> = SettingsSchema.defaults.values) {

    fun raw(key: String): Any? = values[key]

    fun boolean(key: String): Boolean = when (val value = values[key]) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        else -> SettingsSchema.spec(key)?.default as? Boolean ?: false
    }

    fun string(key: String): String = when (val value = values[key]) {
        is String -> value
        null -> SettingsSchema.spec(key)?.default as? String ?: ""
        else -> value.toString()
    }

    fun int(key: String): Int = when (val value = values[key]) {
        is Int -> value
        is Long -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        else -> SettingsSchema.spec(key)?.default as? Int ?: 0
    }

    /** Returns a copy with [key] replaced; keys the schema does not declare are ignored. */
    fun with(key: String, value: Any?): Settings {
        val spec = SettingsSchema.spec(key) ?: return this
        return copy(values = values + (key to (value ?: spec.default)))
    }

    // ------------------------------------------------------------- typed accessors
    val writeId3v1: Boolean get() = boolean("write_id3v1")
    val id3v23: Boolean get() = string("id3v2_mode") == "3"
    val write163Key: Boolean get() = boolean("allow_comment_tag_write_163key")
    val allowDffTags: Boolean get() = boolean("allow_edit_dff_file_tags")
    val forceReplaceAlbumArt: Boolean get() = boolean("force_replace_albumart")
    val coverSizeLimit: Int get() = int("picture_in_file_tags_size_limit")
    val coverFormatLimit: String get() = string("picture_in_file_tags_format_limit")
    val showFileTagsImage: Boolean get() = boolean("is_show_filetags_image")
    val showAlbumArtImage: Boolean get() = boolean("is_show_albumart_image")
    val showFolderImage: Boolean get() = boolean("is_show_folder_image")
    val searchLimit: Int get() = int("web_search_items_limit_count")
    val downloadLyricTranslations: Boolean get() = boolean("download_lyric_translations")
    val translationOnly: Boolean get() = boolean("download_lyric_translations_dont_download_original_lyric")
    val mergeAfterFormat: Boolean get() = boolean("download_lyric_translations_after_format")
    val traditionalToSimplified: Boolean get() = boolean("download_lyric_translations_t2s_mode")
    val connectorLyricTranslation: String get() = string("connectors_lyric_and_tlyric")
    val connectorArtists: String get() = string("connectors_artists")
    val reformatLyricTimeTag: Boolean get() = boolean("reformat_lyric_timetag")
    val removeLyricTimeTag: Boolean get() = boolean("remove_lyric_timetag")
    val deleteLyricBlankLines: Boolean get() = boolean("delete_lyric_blank_text_lines")
    val deleteLyricHeadTags: Boolean get() = boolean("delete_lyric_head_tags")
    val saveLrcWithTags: Boolean get() = boolean("allow_save_lrc_file_while_save_file_tags")
    val lrcEncoding: String get() = string("lrcfiles_encoding")
    val lrcDirectoryMode: String get() = string("lrcfiles_save_directory_mode")
    val lrcFilenameMode: String get() = string("lrcfiles_filename_format_mode")
    val lrcFilenameTemplate: String get() = string("lrcfiles_filename_format")
    val ignoreDurationSeconds: Int get() = int("duration_of_ignore_medias")
    val ignoreDirectories: String get() = string("dirpath_of_ignore_medias")
    val ignoreMp4Videos: Boolean get() = boolean("video_of_ignore_medias")
    val batchRenameMode: String get() = string("batch_rename_mode")
    val batchRenameTemplate: String get() = string("batch_rename_template")
    val darkTheme: Boolean get() = boolean("dark_theme")
    val recentSuggestions: Boolean get() = boolean("search_recent_suggestions")

    /** MP3 write options packed the way the native layer expects (1 = ID3v1, 2 = ID3v2.3). */
    fun writeOptions(): Int = (if (writeId3v1) 1 else 0) or (if (id3v23) 2 else 0)
}

class SettingsRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): Settings {
        val values = SettingsSchema.specs.mapNotNull { spec ->
            val stored: Any? = when (spec.type) {
                SettingType.BOOLEAN ->
                    if (preferences.contains(spec.key)) {
                        preferences.getBoolean(spec.key, spec.default as Boolean)
                    } else {
                        null
                    }
                SettingType.INT ->
                    if (preferences.contains(spec.key)) preferences.getInt(spec.key, spec.default as Int) else null
                SettingType.STRING ->
                    preferences.getString(spec.key, spec.default as String)
            }
            stored?.let { spec.key to it }
        }.toMap()
        return Settings(values)
    }

    fun save(settings: Settings) {
        val editor = preferences.edit()
        for (spec in SettingsSchema.specs) {
            when (spec.type) {
                SettingType.BOOLEAN -> editor.putBoolean(spec.key, settings.boolean(spec.key))
                SettingType.INT -> editor.putInt(spec.key, settings.int(spec.key))
                SettingType.STRING -> editor.putString(spec.key, settings.string(spec.key))
            }
        }
        editor.apply()
    }

    /** Writes one value and returns the updated settings. */
    fun update(key: String, value: Any?): Settings {
        val updated = load().with(key, value)
        save(updated)
        return updated
    }

    fun reset(): Settings {
        preferences.edit().clear().apply()
        return load()
    }

    companion object {
        const val FILE = "musictag_settings"
    }
}
