# 设置项

设置页由 `settings/SettingsSchema.kt` 单源渲染：一个键一条 `SettingSpec`，界面只负责按组
显示。默认值即 `SettingsSchema.defaults`，键名尽量沿用原版 `prefs_settings.xml`。

当前共 **22** 项。可选项（`SettingType.CHOICE`）在界面上是一个选择面板，不再让用户手填；
只有分隔符这类确实需要自由文本的才保留输入框。

## 标签写入（2）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `write_id3v1` | bool | false | MP3 同时写 ID3v1 |
| `id3v2_mode` | string | `4` | `3` 或 `4` |

## 在线搜索（1）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `web_search_items_limit_count` | int | 20 | 单源最多返回条数 |

## 歌词（10）

| 键 | 类型 | 默认 |
|---|---|---|
| `download_lyric_translations` | bool | true |
| `download_lyric_translations_dont_download_original_lyric` | bool | false |
| `download_lyric_translations_after_format` | bool | false |
| `download_lyric_translations_t2s_mode` | bool | false |
| `connectors_lyric_and_tlyric` | string | ` // ` |
| `connectors_artists` | string | ` / ` |
| `reformat_lyric_timetag` | bool | false |
| `remove_lyric_timetag` | bool | false |
| `delete_lyric_blank_text_lines` | bool | false |
| `delete_lyric_head_tags` | bool | false |

## .lrc 文件（3）

| 键 | 类型 | 默认 | 选项 |
|---|---|---|---|
| `allow_save_lrc_file_while_save_file_tags` | bool | true | 保存标签时同时写 `.lrc` |
| `lrcfiles_encoding` | choice | `UTF-8` | UTF-8 / GBK |
| `lrcfiles_filename_format_mode` | choice | `same` | 与音频同名 / 艺术家 - 标题 |

`.lrc` 始终写在音频同一目录；目录模式与自定义模板已移除。

## 曲库（3）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `duration_of_ignore_medias` | choice | `60` | 不启用 / 15 秒 / 30 秒 / 60 秒 / 2 分钟 / 5 分钟 |
| `dirpath_of_ignore_medias` | string | 空 | 换行分隔；由系统文件选择器写入，不用手填 |
| `video_of_ignore_medias` | bool | true | 忽略 MP4 视频 |

## 外观（2）

| 键 | 类型 | 默认 | 选项 |
|---|---|---|---|
| `theme_mode` | choice | `system` | 跟随系统 / 白色主题 / 深色主题 |
| `dynamic_color` | bool | false | 动态取色（Monet，Android 12+） |

主题由 Miuix 的 `ThemeController` 驱动：`theme_mode` 与 `dynamic_color` 组合出
`System`/`Light`/`Dark` 与 `MonetSystem`/`MonetLight`/`MonetDark` 六种模式。

## 高级（1）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `search_recent_suggestions` | bool | true | 记住最近搜索 |

## 已删除

* 1.1.1：三个来源优先级字符串、`web_search_criteria`、主色、强调色、动画、黑主题、
  `tab_items`、手动批量开关、媒体库扫描、批量常亮。
* 1.1.6：`apple_region`、`apple_language`、`apple_cover_size`。
* 1.1.7：`batch_rename_mode`、`batch_rename_template`、
  `lrcfiles_save_directory_mode`、`lrcfiles_filename_format`、`dark_theme`（改为 `theme_mode`）。
