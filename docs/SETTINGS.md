# 设置项

所有偏好只在一处声明（app 模块的 `SettingsSchema`），设置界面由该声明渲染，因此界面
不会和实际存储漂移。存储位置为 `SharedPreferences("musictag_settings")`。

**共 48 项**。`key` 一列在原版语义一致时沿用原版 `pref_settings.xml` 的键名（哪些沿用、
哪些没沿用见 `docs/COMPAT.md`）。标题与说明默认中文；界面标签的英文翻译在
`app/src/main/res/values-en/strings.xml`。

## 标签写入

| key | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `write_id3v1` | boolean | `false` | MP3 同时保留一份 ID3v1 标签。 |
| `id3v2_mode` | string | `4` | 3 或 4。 |
| `allow_comment_tag_write_163key` | boolean | `false` | 把网易云 payload 写进 comment 字段。 |
| `allow_edit_dff_file_tags` | boolean | `true` | DSD 文件里的 ID3 区块。 |
| `force_replace_albumart` | boolean | `false` | 保存时覆盖已有封面。 |

## 封面

| key | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `picture_in_file_tags_size_limit` | int | `0` | 像素；0 表示不限制。 |
| `picture_in_file_tags_format_limit` | string | `` | 留空表示不限格式。 |
| `is_show_filetags_image` | boolean | `true` | 使用标签内的封面。 |
| `is_show_albumart_image` | boolean | `false` | 使用专辑封面。 |
| `is_show_folder_image` | boolean | `false` | 使用文件夹封面。 |

## 在线搜索

| key | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `tags_sources_combination` | string | `netease,qq,kuwo,kugou,apple,itunes,musicbrainz` | 合并标签时的来源优先级；四个中文源在前，英文源兜底。 |
| `tags_sources_picture` | string | `apple,netease,qq,itunes,coverartarchive,kuwo,musicbrainz,kugou` | 封面来源优先级；Apple 分辨率最高。 |
| `tags_sources_lyric` | string | `netease,qq,kugou,kuwo` | 歌词来源优先级；只有这四家提供歌词。 |
| `web_search_criteria` | string | `title,artist` | 逗号分隔：title、artist、album。 |
| `web_search_items_limit_count` | int | `20` | 每个来源最多返回多少条。 |
| `apple_region` | string | `cn` | Apple Music 地区：cn、us、jp、kr、hk、tw 等。 |
| `apple_language` | string | `` | 留空则跟随地区，中文区为 zh-Hans。 |
| `apple_cover_size` | int | `3000` | Apple Music 封面边长（像素，120–5000）。 |

`tags_sources_*` 里的来源键固定为：`netease`、`qq`、`kuwo`、`kugou`、`apple`、`itunes`、
`musicbrainz`、`coverartarchive`。无法识别的键（包括已下线的 `vgmdb`、`lastfm`）会被
静默忽略，因此旧配置不会导致崩溃。

## 歌词

| key | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `download_lyric_translations` | boolean | `true` | 下载翻译歌词。 |
| `download_lyric_translations_dont_download_original_lyric` | boolean | `false` | 只要翻译。 |
| `download_lyric_translations_after_format` | boolean | `false` | 格式化后再合并。 |
| `download_lyric_translations_t2s_mode` | boolean | `false` | 对下载的歌词与标签生效。 |
| `connectors_lyric_and_tlyric` | string | ` // ` | 原文与译文的分隔符。 |
| `connectors_artists` | string | ` / ` | 艺术家分隔符。 |
| `reformat_lyric_timetag` | boolean | `false` | 保存时统一成两位小数。 |
| `remove_lyric_timetag` | boolean | `false` | 移除时间戳。 |
| `delete_lyric_blank_text_lines` | boolean | `false` | 删除空行。 |
| `delete_lyric_head_tags` | boolean | `false` | 删除 `[ti:]` 类标签。 |

## .lrc 文件

| key | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `allow_save_lrc_file_while_save_file_tags` | boolean | `true` | 保存标签时同时写 `.lrc`。 |
| `lrcfiles_encoding` | string | `UTF-8` | `.lrc` 编码。 |
| `lrcfiles_save_directory_mode` | string | `alongside` | alongside、music 或 custom。 |
| `lrcfiles_filename_format_mode` | string | `same` | same、artist-title 或 custom。 |
| `lrcfiles_filename_format` | string | `%t - %a` | `%t` 标题、`%a` 艺术家、`%l` 专辑。 |

## 曲库

| key | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `duration_of_ignore_medias` | int | `0` | 秒；0 表示不启用。 |
| `dirpath_of_ignore_medias` | string | `` | 换行分隔。 |
| `video_of_ignore_medias` | boolean | `true` | 忽略 MP4 视频。 |

## 批量处理

| key | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `always_show_batch_buttons` | boolean | `false` | 总是显示批量操作。 |
| `batch_rename_mode` | string | `tags` | tags 或 name。 |
| `batch_rename_template` | string | `%a - %t` | `%t` 标题、`%a` 艺术家、`%l` 专辑、`%n` 音轨。 |

## 外观

| key | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `dark_theme` | boolean | `false` | 深色主题。 |
| `allow_black_theme` | boolean | `true` | 允许纯黑。 |
| `primary_color` | string | `default` | 主色。 |
| `accent_color` | string | `default` | 强调色。 |
| `tab_items` | string | `` | 留空表示使用全部默认标签页。 |
| `enable_animations` | boolean | `true` | 动画。 |

## 高级

| key | 类型 | 默认值 | 含义 |
|---|---|---|---|
| `search_recent_suggestions` | boolean | `true` | 记住最近搜索。 |
| `media_store_rescan` | boolean | `true` | 保存后刷新媒体库。 |
| `keep_screen_on_while_batch` | boolean | `true` | 批量处理时保持亮屏。 |
