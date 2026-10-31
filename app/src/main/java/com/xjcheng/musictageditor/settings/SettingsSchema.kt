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
    APPEARANCE("外观"),
    ADVANCED("高级"),
}

enum class SettingType { BOOLEAN, STRING, INT, CHOICE }

/** One selectable value of a [SettingType.CHOICE] preference. */
data class SettingOption(val value: String, val label: String)

/**
 * One stored preference.
 *
 * [key] keeps the original app's preference name wherever the semantics match. Only
 * preferences the app actually reads are declared: manual text entry was replaced by
 * [SettingType.CHOICE] pickers, and settings that changed nothing were removed.
 */
data class SettingSpec(
    val key: String,
    val type: SettingType,
    val default: Any,
    val title: String,
    val group: SettingGroup,
    val summary: String = "",
    val options: List<SettingOption> = emptyList(),
) {
    /** The label of the current choice, for the row summary. */
    fun labelOf(value: String): String =
        options.firstOrNull { it.value == value }?.label ?: value
}

/**
 * The complete preference schema.
 *
 * Every entry is validated by tests: defaults decode to the documented values, encode and
 * decode round-trip, and no key is declared twice.
 */
object SettingsSchema {

    private val themeOptions = listOf(
        SettingOption("system", "跟随系统"),
        SettingOption("light", "白色主题"),
        SettingOption("dark", "深色主题"),
    )

    private val encodingOptions = listOf(
        SettingOption("UTF-8", "UTF-8"),
        SettingOption("GBK", "GBK"),
    )

    private val lrcNameOptions = listOf(
        SettingOption("same", "与音频同名"),
        SettingOption("artist-title", "艺术家 - 标题"),
    )

    private val durationOptions = listOf(
        SettingOption("0", "不启用"),
        SettingOption("15", "15 秒"),
        SettingOption("30", "30 秒"),
        SettingOption("60", "60 秒"),
        SettingOption("120", "2 分钟"),
        SettingOption("300", "5 分钟"),
    )

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
        SettingSpec("lrcfiles_encoding", SettingType.CHOICE, "UTF-8", ".lrc 编码", SettingGroup.LRC_FILES, "只影响写出的 .lrc 文件。", encodingOptions),
        SettingSpec("lrcfiles_filename_format_mode", SettingType.CHOICE, "same", ".lrc 文件名", SettingGroup.LRC_FILES, "与音频同名，或「艺术家 - 标题」。", lrcNameOptions),

        // -------------------------------------------------------------------- library
        SettingSpec("duration_of_ignore_medias", SettingType.CHOICE, "60", "忽略短于该时长的文件", SettingGroup.LIBRARY, "常见提示音不会出现在曲库里。", durationOptions),
        SettingSpec("dirpath_of_ignore_medias", SettingType.STRING, "", "忽略的目录", SettingGroup.LIBRARY, "点按用系统文件选择器添加。"),
        SettingSpec("video_of_ignore_medias", SettingType.BOOLEAN, true, "忽略 MP4 视频", SettingGroup.LIBRARY),

        // ---------------------------------------------------------------- appearance
        SettingSpec("theme_mode", SettingType.CHOICE, "system", "主题颜色", SettingGroup.APPEARANCE, "跟随系统、白色或深色。", themeOptions),
        SettingSpec("dynamic_color", SettingType.BOOLEAN, false, "动态取色", SettingGroup.APPEARANCE, "从壁纸提取主题色（Android 12+）。"),

        // ------------------------------------------------------------------ advanced
        SettingSpec("verbose_logging", SettingType.BOOLEAN, false, "全量日志", SettingGroup.ADVANCED, "记录每次搜索、读写与来源调用的细节，排查问题时打开。"),
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
