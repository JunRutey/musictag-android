/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.settings

/** Groups used by the settings screen. */
enum class SettingGroup(val title: String) {
    WRITING("标签写入"),
    SEARCH("在线搜索"),
    LYRICS("歌词"),
    LRC_FILES(".lrc 文件"),
    LIBRARY("曲库"),
    BATCH("批量处理"),
    APPEARANCE("外观"),
    ADVANCED("高级"),
}

enum class SettingType { BOOLEAN, STRING, INT }

/**
 * One stored preference.
 *
 * [key] keeps the original app's preference name wherever the semantics match, so a future
 * import of legacy settings has something to map onto (see docs/SETTINGS.md).
 *
 * Only preferences the app actually reads are declared here: the source-priority strings,
 * the unused colour and animation switches, and the manual batch switches were removed in
 * 1.1.1 because toggling them changed nothing.
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
        SettingSpec("write_id3v1", SettingType.BOOLEAN, false, "同时写入 ID3v1", SettingGroup.WRITING, "MP3 会保留一份 ID3v1 标签，兼容老设备。"),
        SettingSpec("id3v2_mode", SettingType.STRING, "4", "ID3v2 版本", SettingGroup.WRITING, "3 或 4。"),

        // -------------------------------------------------------------- online search
        SettingSpec("web_search_items_limit_count", SettingType.INT, 20, "单源结果数", SettingGroup.SEARCH, "每个来源最多返回多少条。"),

        // --------------------------------------------------------------------- lyrics
        SettingSpec("download_lyric_translations", SettingType.BOOLEAN, true, "下载翻译歌词", SettingGroup.LYRICS),
        SettingSpec("download_lyric_translations_dont_download_original_lyric", SettingType.BOOLEAN, false, "只要翻译", SettingGroup.LYRICS),
        SettingSpec("download_lyric_translations_after_format", SettingType.BOOLEAN, false, "格式化后合并", SettingGroup.LYRICS),
        SettingSpec("download_lyric_translations_t2s_mode", SettingType.BOOLEAN, false, "繁体转简体", SettingGroup.LYRICS, "对下载的歌词与标签生效。"),
        SettingSpec("connectors_lyric_and_tlyric", SettingType.STRING, " // ", "原文/译文分隔符", SettingGroup.LYRICS),
        SettingSpec("connectors_artists", SettingType.STRING, " / ", "艺术家分隔符", SettingGroup.LYRICS),
        SettingSpec("reformat_lyric_timetag", SettingType.BOOLEAN, false, "规范化时间戳", SettingGroup.LYRICS, "保存时统一成两位小数。"),
        SettingSpec("remove_lyric_timetag", SettingType.BOOLEAN, false, "移除时间戳", SettingGroup.LYRICS),
        SettingSpec("delete_lyric_blank_text_lines", SettingType.BOOLEAN, false, "删除空行", SettingGroup.LYRICS),
        SettingSpec("delete_lyric_head_tags", SettingType.BOOLEAN, false, "删除 [ti:] 类标签", SettingGroup.LYRICS),

        // ------------------------------------------------------------------ .lrc files
        SettingSpec("allow_save_lrc_file_while_save_file_tags", SettingType.BOOLEAN, true, "同时写入 .lrc 文件", SettingGroup.LRC_FILES),
        SettingSpec("lrcfiles_encoding", SettingType.STRING, "UTF-8", ".lrc 编码", SettingGroup.LRC_FILES),
        SettingSpec("lrcfiles_save_directory_mode", SettingType.STRING, "alongside", ".lrc 目录", SettingGroup.LRC_FILES, "alongside、music 或 custom。"),
        SettingSpec("lrcfiles_filename_format_mode", SettingType.STRING, "same", ".lrc 文件名", SettingGroup.LRC_FILES, "same、artist-title 或 custom。"),
        SettingSpec("lrcfiles_filename_format", SettingType.STRING, "%t - %a", "文件名模板", SettingGroup.LRC_FILES, "%t 标题、%a 艺术家、%l 专辑。"),

        // -------------------------------------------------------------------- library
        SettingSpec("duration_of_ignore_medias", SettingType.INT, 0, "忽略短于该时长的文件", SettingGroup.LIBRARY, "秒；0 表示不启用。"),
        SettingSpec("dirpath_of_ignore_medias", SettingType.STRING, "", "忽略的目录", SettingGroup.LIBRARY, "换行分隔。"),
        SettingSpec("video_of_ignore_medias", SettingType.BOOLEAN, true, "忽略 MP4 视频", SettingGroup.LIBRARY),

        // --------------------------------------------------------------------- batch
        SettingSpec("batch_rename_mode", SettingType.STRING, "tags", "批量重命名依据", SettingGroup.BATCH, "tags 或 name。"),
        SettingSpec("batch_rename_template", SettingType.STRING, "%a - %t", "重命名模板", SettingGroup.BATCH, "%t 标题、%a 艺术家、%l 专辑、%n 音轨。"),

        // ---------------------------------------------------------------- appearance
        SettingSpec("dark_theme", SettingType.BOOLEAN, false, "深色主题", SettingGroup.APPEARANCE, "跟随系统深色，或在此强制开启。"),

        // ------------------------------------------------------------------ advanced
        SettingSpec("search_recent_suggestions", SettingType.BOOLEAN, true, "记住最近搜索", SettingGroup.ADVANCED),
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
