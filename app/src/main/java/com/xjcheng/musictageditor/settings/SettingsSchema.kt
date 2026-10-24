/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.settings

/** Groups used by the settings screen. */
enum class SettingGroup(val title: String) {
    WRITING("Tag writing"),
    COVERS("Covers"),
    SEARCH("Online search"),
    LYRICS("Lyrics"),
    LRC_FILES(".lrc files"),
    LIBRARY("Library"),
    BATCH("Batch editing"),
    APPEARANCE("Appearance"),
    ADVANCED("Advanced"),
}

enum class SettingType { BOOLEAN, STRING, INT }

/**
 * One stored preference.
 *
 * [key] keeps the original app's preference name wherever the semantics match, so a future
 * import of legacy settings has something to map onto (see docs/SETTINGS.md).
 */
data class SettingSpec(
    val key: String,
    val type: SettingType,
    val default: Any,
    val title: String,
    val group: SettingGroup,
    val summary: String = "",
)

/**
 * The complete preference schema.
 *
 * Every entry is validated by tests: defaults decode to the documented values, encode and
 * decode round-trip, and no key is declared twice.
 */
object SettingsSchema {

    val specs: List<SettingSpec> = listOf(
        // ---------------------------------------------------------------- tag writing
        SettingSpec("write_id3v1", SettingType.BOOLEAN, false, "Also write ID3v1", SettingGroup.WRITING, "MP3 files keep a companion ID3v1 tag."),
        SettingSpec("id3v2_mode", SettingType.STRING, "4", "ID3v2 version", SettingGroup.WRITING, "3 or 4."),
        SettingSpec("allow_comment_tag_write_163key", SettingType.BOOLEAN, false, "Write the 163 key marker", SettingGroup.WRITING, "Store the NetEase payload in the comment."),
        SettingSpec("allow_edit_dff_file_tags", SettingType.BOOLEAN, true, "Edit DFF tags", SettingGroup.WRITING, "DSD files carry an ID3 chunk."),
        SettingSpec("force_replace_albumart", SettingType.BOOLEAN, false, "Always replace artwork", SettingGroup.WRITING, "Overwrite existing artwork on save."),

        // --------------------------------------------------------------------- covers
        SettingSpec("picture_in_file_tags_size_limit", SettingType.INT, 0, "Embedded cover size limit", SettingGroup.COVERS, "Pixels; 0 means no limit."),
        SettingSpec("picture_in_file_tags_format_limit", SettingType.STRING, "", "Embedded cover formats", SettingGroup.COVERS, "Empty means any format."),
        SettingSpec("is_show_filetags_image", SettingType.BOOLEAN, true, "Use artwork from tags", SettingGroup.COVERS),
        SettingSpec("is_show_albumart_image", SettingType.BOOLEAN, false, "Use album artwork", SettingGroup.COVERS),
        SettingSpec("is_show_folder_image", SettingType.BOOLEAN, false, "Use folder artwork", SettingGroup.COVERS),

        // -------------------------------------------------------------- online search
        SettingSpec("tags_sources_combination", SettingType.STRING, "musicbrainz,itunes,netease,qq,kuwo,kugou,vgmdb,lastfm", "Tag sources", SettingGroup.SEARCH, "Priority order for combined tags."),
        SettingSpec("tags_sources_picture", SettingType.STRING, "coverartarchive,itunes,netease,qq,musicbrainz,kuwo", "Cover sources", SettingGroup.SEARCH, "Priority order for artwork."),
        SettingSpec("tags_sources_lyric", SettingType.STRING, "netease,qq,kugou,kuwo", "Lyrics sources", SettingGroup.SEARCH, "Priority order for lyrics."),
        SettingSpec("web_search_criteria", SettingType.STRING, "title,artist", "Match on", SettingGroup.SEARCH, "Comma separated: title, artist, album."),
        SettingSpec("web_search_items_limit_count", SettingType.INT, 20, "Result limit", SettingGroup.SEARCH, "Results per source."),
        SettingSpec("lastfm_api_key", SettingType.STRING, "", "Last.fm API key", SettingGroup.SEARCH, "No key is shipped; add your own to enable Last.fm."),

        // --------------------------------------------------------------------- lyrics
        SettingSpec("download_lyric_translations", SettingType.BOOLEAN, true, "Download translations", SettingGroup.LYRICS),
        SettingSpec("download_lyric_translations_dont_download_original_lyric", SettingType.BOOLEAN, false, "Translation only", SettingGroup.LYRICS),
        SettingSpec("download_lyric_translations_after_format", SettingType.BOOLEAN, false, "Merge after formatting", SettingGroup.LYRICS),
        SettingSpec("download_lyric_translations_t2s_mode", SettingType.BOOLEAN, false, "Convert traditional to simplified", SettingGroup.LYRICS, "Applies to downloaded lyrics and tags."),
        SettingSpec("connectors_lyric_and_tlyric", SettingType.STRING, " // ", "Original/translation separator", SettingGroup.LYRICS),
        SettingSpec("connectors_artists", SettingType.STRING, " / ", "Artist separator", SettingGroup.LYRICS),
        SettingSpec("reformat_lyric_timetag", SettingType.BOOLEAN, false, "Normalise timestamps", SettingGroup.LYRICS, "Rewrite to two decimals on save."),
        SettingSpec("remove_lyric_timetag", SettingType.BOOLEAN, false, "Strip timestamps", SettingGroup.LYRICS),
        SettingSpec("delete_lyric_blank_text_lines", SettingType.BOOLEAN, false, "Drop blank lines", SettingGroup.LYRICS),
        SettingSpec("delete_lyric_head_tags", SettingType.BOOLEAN, false, "Drop [ti:] style tags", SettingGroup.LYRICS),

        // ------------------------------------------------------------------ .lrc files
        SettingSpec("allow_save_lrc_file_while_save_file_tags", SettingType.BOOLEAN, true, "Write .lrc files too", SettingGroup.LRC_FILES),
        SettingSpec("lrcfiles_encoding", SettingType.STRING, "UTF-8", ".lrc encoding", SettingGroup.LRC_FILES),
        SettingSpec("lrcfiles_save_directory_mode", SettingType.STRING, "alongside", ".lrc directory", SettingGroup.LRC_FILES, "alongside, music, or custom."),
        SettingSpec("lrcfiles_filename_format_mode", SettingType.STRING, "same", ".lrc file name", SettingGroup.LRC_FILES, "same, artist-title, or custom."),
        SettingSpec("lrcfiles_filename_format", SettingType.STRING, "%t - %a", "File name template", SettingGroup.LRC_FILES, "%t title, %a artist, %l album."),

        // -------------------------------------------------------------------- library
        SettingSpec("duration_of_ignore_medias", SettingType.INT, 0, "Ignore files shorter than", SettingGroup.LIBRARY, "Seconds; 0 disables."),
        SettingSpec("dirpath_of_ignore_medias", SettingType.STRING, "", "Ignored directories", SettingGroup.LIBRARY, "Newline separated."),
        SettingSpec("video_of_ignore_medias", SettingType.BOOLEAN, true, "Ignore MP4 videos", SettingGroup.LIBRARY),

        // --------------------------------------------------------------------- batch
        SettingSpec("always_show_batch_buttons", SettingType.BOOLEAN, false, "Always show batch actions", SettingGroup.BATCH),
        SettingSpec("batch_rename_mode", SettingType.STRING, "tags", "Batch rename", SettingGroup.BATCH, "tags or name."),
        SettingSpec("batch_rename_template", SettingType.STRING, "%a - %t", "Rename template", SettingGroup.BATCH, "%t title, %a artist, %l album, %n track."),

        // ---------------------------------------------------------------- appearance
        SettingSpec("dark_theme", SettingType.BOOLEAN, false, "Dark theme", SettingGroup.APPEARANCE),
        SettingSpec("allow_black_theme", SettingType.BOOLEAN, true, "Allow pure black", SettingGroup.APPEARANCE),
        SettingSpec("primary_color", SettingType.STRING, "default", "Primary colour", SettingGroup.APPEARANCE),
        SettingSpec("accent_color", SettingType.STRING, "default", "Accent colour", SettingGroup.APPEARANCE),
        SettingSpec("tab_items", SettingType.STRING, "", "Tabs", SettingGroup.APPEARANCE, "Empty means all default tabs."),
        SettingSpec("enable_animations", SettingType.BOOLEAN, true, "Animations", SettingGroup.APPEARANCE),

        // ------------------------------------------------------------------ advanced
        SettingSpec("search_recent_suggestions", SettingType.BOOLEAN, true, "Remember recent searches", SettingGroup.ADVANCED),
        SettingSpec("media_store_rescan", SettingType.BOOLEAN, true, "Refresh MediaStore after saving", SettingGroup.ADVANCED),
        SettingSpec("keep_screen_on_while_batch", SettingType.BOOLEAN, true, "Keep the screen on during batch runs", SettingGroup.ADVANCED),
    )

    private val byKey: Map<String, SettingSpec> = specs.associateBy { it.key }

    init {
        check(byKey.size == specs.size) { "duplicate preference keys" }
    }

    fun spec(key: String): SettingSpec? = byKey[key]

    /** Defaults, grouped in schema order. */
    val defaults: Settings = Settings(
        specs.associate { it.key to it.default },
    )
}
