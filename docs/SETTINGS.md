# Settings

Every preference is declared once in `SettingsSchema` (app module) and rendered from
that declaration, so the screen cannot drift from what is stored. Storage is
`SharedPreferences("musictag_settings")`.

**46 keys** are implemented. The `key` column keeps the original app's
preference name wherever the semantics match (see `docs/COMPAT.md` for what is and is
not carried over).

## Tag writing

| key | type | default | meaning |
|---|---|---|---|
| `write_id3v1` | boolean | `false` | MP3 files keep a companion ID3v1 tag. |
| `id3v2_mode` | string | `4` | 3 or 4. |
| `allow_comment_tag_write_163key` | boolean | `false` | Store the NetEase payload in the comment. |
| `allow_edit_dff_file_tags` | boolean | `true` | DSD files carry an ID3 chunk. |
| `force_replace_albumart` | boolean | `false` | Overwrite existing artwork on save. |

## Covers

| key | type | default | meaning |
|---|---|---|---|
| `picture_in_file_tags_size_limit` | int | `0` | Pixels; 0 means no limit. |
| `picture_in_file_tags_format_limit` | string | `` | Empty means any format. |
| `is_show_filetags_image` | boolean | `true` | Use artwork from tags |
| `is_show_albumart_image` | boolean | `false` | Use album artwork |
| `is_show_folder_image` | boolean | `false` | Use folder artwork |

## Online search

| key | type | default | meaning |
|---|---|---|---|
| `tags_sources_combination` | string | `musicbrainz,itunes,netease,qq,kuwo,kugou,vgmdb,lastfm` | Priority order for combined tags. |
| `tags_sources_picture` | string | `coverartarchive,itunes,netease,qq,musicbrainz,kuwo` | Priority order for artwork. |
| `tags_sources_lyric` | string | `netease,qq,kugou,kuwo` | Priority order for lyrics. |
| `web_search_criteria` | string | `title,artist` | Comma separated: title, artist, album. |
| `web_search_items_limit_count` | int | `20` | Results per source. |
| `lastfm_api_key` | string | `` | No key is shipped; add your own to enable Last.fm. |

## Lyrics

| key | type | default | meaning |
|---|---|---|---|
| `download_lyric_translations` | boolean | `true` | Download translations |
| `download_lyric_translations_dont_download_original_lyric` | boolean | `false` | Translation only |
| `download_lyric_translations_after_format` | boolean | `false` | Merge after formatting |
| `download_lyric_translations_t2s_mode` | boolean | `false` | Applies to downloaded lyrics and tags. |
| `connectors_lyric_and_tlyric` | string | ` // ` | Original/translation separator |
| `connectors_artists` | string | ` / ` | Artist separator |
| `reformat_lyric_timetag` | boolean | `false` | Rewrite to two decimals on save. |
| `remove_lyric_timetag` | boolean | `false` | Strip timestamps |
| `delete_lyric_blank_text_lines` | boolean | `false` | Drop blank lines |
| `delete_lyric_head_tags` | boolean | `false` | Drop [ti:] style tags |

## .lrc files

| key | type | default | meaning |
|---|---|---|---|
| `allow_save_lrc_file_while_save_file_tags` | boolean | `true` | Write .lrc files too |
| `lrcfiles_encoding` | string | `UTF-8` | .lrc encoding |
| `lrcfiles_save_directory_mode` | string | `alongside` | alongside, music, or custom. |
| `lrcfiles_filename_format_mode` | string | `same` | same, artist-title, or custom. |
| `lrcfiles_filename_format` | string | `%t - %a` | %t title, %a artist, %l album. |

## Library

| key | type | default | meaning |
|---|---|---|---|
| `duration_of_ignore_medias` | int | `0` | Seconds; 0 disables. |
| `dirpath_of_ignore_medias` | string | `` | Newline separated. |
| `video_of_ignore_medias` | boolean | `true` | Ignore MP4 videos |

## Batch editing

| key | type | default | meaning |
|---|---|---|---|
| `always_show_batch_buttons` | boolean | `false` | Always show batch actions |
| `batch_rename_mode` | string | `tags` | tags or name. |
| `batch_rename_template` | string | `%a - %t` | %t title, %a artist, %l album, %n track. |

## Appearance

| key | type | default | meaning |
|---|---|---|---|
| `dark_theme` | boolean | `false` | Dark theme |
| `allow_black_theme` | boolean | `true` | Allow pure black |
| `primary_color` | string | `default` | Primary colour |
| `accent_color` | string | `default` | Accent colour |
| `tab_items` | string | `` | Empty means all default tabs. |
| `enable_animations` | boolean | `true` | Animations |

## Advanced

| key | type | default | meaning |
|---|---|---|---|
| `search_recent_suggestions` | boolean | `true` | Remember recent searches |
| `media_store_rescan` | boolean | `true` | Refresh MediaStore after saving |
| `keep_screen_on_while_batch` | boolean | `true` | Keep the screen on during batch runs |

