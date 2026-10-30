/*
 * Copyright 2026 The Music Tag (musictag-android) authors.
 * Licensed under the Apache License, Version 2.0.
 */

package com.xjcheng.musictageditor.settings

/** Groups used by the settings screen. */
enum class SettingGroup(val title: String) {
    WRITING("标签写入"),
    COVERS("封面"),
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
 * import of legacy settings has something to map onto (see docs/SETTINGS.md). Titles and
 * summaries are Chinese because that is the app's default copy; English lives in
 * `values-en/strings.xml` for the labels that come from resources.
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

    /** Four Chinese sources first, then Apple Music/iTunes/MusicBrainz as the fallback. */
    const val DEFAULT_TAG_SOURCES = "netease,qq,kuwo,kugou,apple,itunes,musicbrainz"

    /** Apple returns the highest resolution artwork, so it leads. */
    const val DEFAULT_COVER_SOURCES = "apple,netease,qq,itunes,coverartarchive,kuwo,musicbrainz,kugou"

    const val DEFAULT_LYRIC_SOURCES = "netease,qq,kugou,kuwo"

    val specs: List<SettingSpec> = listOf(
        // ---------------------------------------------------------------- tag writing
        SettingSpec("write_id3v1", SettingType.BOOLEAN, false, "同时写入 ID3v1", SettingGroup.WRITING, "MP3 会保留一份 ID3v1 标签，兼容老设备。"),
        SettingSpec("id3v2_mode", SettingType.STRING, "4", "ID3v2 版本", SettingGroup.WRITING, "3 或 4。"),
        SettingSpec("allow_comment_tag_write_163key", SettingType.BOOLEAN, false, "写入 163 key 标记", SettingGroup.WRITING, "把网易云 payload 写进 comment 字段。"),
        SettingSpec("allow_edit_dff_file_tags", SettingType.BOOLEAN, true, "编辑 DFF 标签", SettingGroup.WRITING, "DSD 文件里的 ID3 区块。"),
        SettingSpec("force_replace_albumart", SettingType.BOOLEAN, false, "总是替换封面", SettingGroup.WRITING, "保存时覆盖已有封面。"),

        // --------------------------------------------------------------------- covers
        SettingSpec("picture_in_file_tags_size_limit", SettingType.INT, 0, "内嵌封面尺寸上限", SettingGroup.COVERS, "像素；0 表示不限制。"),
        SettingSpec("picture_in_file_tags_format_limit", SettingType.STRING, "", "内嵌封面格式", SettingGroup.COVERS, "留空表示不限格式。"),
        SettingSpec("is_show_filetags_image", SettingType.BOOLEAN, true, "使用标签内的封面", SettingGroup.COVERS),
        SettingSpec("is_show_albumart_image", SettingType.BOOLEAN, false, "使用专辑封面", SettingGroup.COVERS),
        SettingSpec("is_show_folder_image", SettingType.BOOLEAN, false, "使用文件夹封面", SettingGroup.COVERS),

        // -------------------------------------------------------------- online search
        SettingSpec("tags_sources_combination", SettingType.STRING, DEFAULT_TAG_SOURCES, "标签来源优先级", SettingGroup.SEARCH, "合并标签时按此顺序取第一个非空值；英文源兜底。"),
        SettingSpec("tags_sources_picture", SettingType.STRING, DEFAULT_COVER_SOURCES, "封面来源优先级", SettingGroup.SEARCH, "Apple 的封面分辨率最高，因此排在最前。"),
        SettingSpec("tags_sources_lyric", SettingType.STRING, DEFAULT_LYRIC_SOURCES, "歌词来源优先级", SettingGroup.SEARCH, "只有网易云、QQ、酷狗、酷我能提供歌词。"),
        SettingSpec("web_search_criteria", SettingType.STRING, "title,artist", "匹配字段", SettingGroup.SEARCH, "逗号分隔：title、artist、album。"),
        SettingSpec("web_search_items_limit_count", SettingType.INT, 20, "单源结果数", SettingGroup.SEARCH, "每个来源最多返回多少条。"),
        SettingSpec("apple_region", SettingType.STRING, "cn", "Apple Music 地区", SettingGroup.SEARCH, "cn、us、jp、kr、hk、tw 等。"),
        SettingSpec("apple_language", SettingType.STRING, "", "Apple Music 语言", SettingGroup.SEARCH, "留空则跟随地区，中文区为 zh-Hans。"),
        SettingSpec("apple_cover_size", SettingType.INT, 3000, "Apple Music 封面尺寸", SettingGroup.SEARCH, "像素，120–5000。"),

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
        SettingSpec("always_show_batch_buttons", SettingType.BOOLEAN, false, "总是显示批量操作", SettingGroup.BATCH),
        SettingSpec("batch_rename_mode", SettingType.STRING, "tags", "批量重命名依据", SettingGroup.BATCH, "tags 或 name。"),
        SettingSpec("batch_rename_template", SettingType.STRING, "%a - %t", "重命名模板", SettingGroup.BATCH, "%t 标题、%a 艺术家、%l 专辑、%n 音轨。"),

        // ---------------------------------------------------------------- appearance
        SettingSpec("dark_theme", SettingType.BOOLEAN, false, "深色主题", SettingGroup.APPEARANCE),
        SettingSpec("allow_black_theme", SettingType.BOOLEAN, true, "允许纯黑", SettingGroup.APPEARANCE),
        SettingSpec("primary_color", SettingType.STRING, "default", "主色", SettingGroup.APPEARANCE),
        SettingSpec("accent_color", SettingType.STRING, "default", "强调色", SettingGroup.APPEARANCE),
        SettingSpec("tab_items", SettingType.STRING, "", "底部标签页", SettingGroup.APPEARANCE, "留空表示使用全部默认标签页。"),
        SettingSpec("enable_animations", SettingType.BOOLEAN, true, "动画", SettingGroup.APPEARANCE),

        // ------------------------------------------------------------------ advanced
        SettingSpec("search_recent_suggestions", SettingType.BOOLEAN, true, "记住最近搜索", SettingGroup.ADVANCED),
        SettingSpec("media_store_rescan", SettingType.BOOLEAN, true, "保存后刷新媒体库", SettingGroup.ADVANCED),
        SettingSpec("keep_screen_on_while_batch", SettingType.BOOLEAN, true, "批量处理时保持亮屏", SettingGroup.ADVANCED),
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
