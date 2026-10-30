# 设置项

设置页由 `settings/SettingsSchema.kt` 单源渲染：一个键一条 `SettingSpec`，界面只负责按组
显示，不再手写布局。默认值即 `SettingsSchema.defaults`，键名尽量沿用原版
`pref_settings.xml`，方便将来导入旧配置。

当前共 **28** 项。1.1.1 删掉了 12 项“改了也看不出区别”的开关（来源优先级字符串、主色/
强调色、动画、黑主题、Tab 项、手动批量开关等），只保留真正影响行为的项。

## 标签写入（2）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `write_id3v1` | bool | false | MP3 同时写 ID3v1 |
| `id3v2_mode` | string | `4` | `3` 或 `4` |

## 在线搜索（4）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `web_search_items_limit_count` | int | 20 | 单源最多返回条数 |
| `apple_region` | string | `cn` | Apple Music 地区 |
| `apple_language` | string | 空 | Apple Music 语言，空则跟随地区 |
| `apple_cover_size` | int | 3000 | Apple 封面边长（像素） |

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

## .lrc 文件（5）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `allow_save_lrc_file_while_save_file_tags` | bool | true | 保存标签时同时写 `.lrc` |
| `lrcfiles_encoding` | string | `UTF-8` | 写 `.lrc` 的编码 |
| `lrcfiles_save_directory_mode` | string | `alongside` | `alongside` / `music` / `custom` |
| `lrcfiles_filename_format_mode` | string | `same` | `same` / `artist-title` / `custom` |
| `lrcfiles_filename_format` | string | `%t - %a` | 模板：`%t` 标题、`%a` 艺术家、`%l` 专辑 |

## 曲库（3）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `duration_of_ignore_medias` | int | 0 | 忽略短于该秒数的文件，0 关闭 |
| `dirpath_of_ignore_medias` | string | 空 | 换行分隔的忽略目录 |
| `video_of_ignore_medias` | bool | true | 忽略 MP4 视频 |

## 批量处理（2）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `batch_rename_mode` | string | `tags` | `tags` / `name` |
| `batch_rename_template` | string | `%a - %t` | 重命名模板 |

## 外观（1）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `dark_theme` | bool | false | 强制深色；关掉时跟随系统 |

## 高级（1）

| 键 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `search_recent_suggestions` | bool | true | 记住最近搜索 |

## 已删除（1.1.1）

`tags_sources_combination`、`tags_sources_picture`、`tags_sources_lyric`、
`web_search_criteria`、`primary_color`、`accent_color`、`allow_black_theme`、
`enable_animations`、`tab_items`、`always_show_batch_buttons`、`media_store_rescan`、
`keep_screen_on_while_batch`。旧配置里的这些键会被忽略；来源优先级固定为
`MergePolicy.DEFAULT`（见 `docs/SOURCES.md`）。
