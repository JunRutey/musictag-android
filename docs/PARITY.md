# Feature parity tracker

Every user-visible capability of `com.xjcheng.musictageditor` 1.2.5.2, with the milestone
that delivers it and the check that proves it. "Parity" here means *the same capability*,
not the same pixels: the UI is rebuilt in Compose.

Status values: `todo` / `wip` / `done` / `dropped`.

构建与发版流程见 [docs/RELEASING.md](RELEASING.md)：日常改动构建 `.debug` 后缀的验证包，
确认后才打 tag 出签名发行版。

## 0. 1.1.0 实际状态（以此表为准）

下面按能力组给出 1.1.0 的真实状态；第 1 节之后的逐行表格是最初的计划清单，行内状态
尚未逐条回填，请以本表为准。

| 能力组 | 状态 | 证据 |
|---|---|---|
| 曲库浏览：歌曲/艺术家/专辑三 Tab、拼音分组与索引、四种排序 | done | `LibraryEntriesTest`、`PinyinIndexTest`、`MediaGroupingTest` |
| 曲库行：封面、格式角标、时长与码率 | done | `AlbumArtLoader`（无真机验证，见 docs/UI.md） |
| 标签编辑：10 个字段 + 歌词 + 封面 + 艺术家图片 | done | `TagLibWriteTest`（MP3/FLAC/M4A/APE/Ogg 往返） |
| ID3v1 / ID3v2.3 选项 | done | `SettingsSchemaTest`、`TagLibWriteTest.honoursId3v23Option` |
| 在线搜索与合并：网易/QQ/酷我/酷狗 + Apple/iTunes/MusicBrainz | done | `:core:net` 71+ 用例、`ProviderRegistryTest` |
| 封面来源与优先级设置 | done | `MergePolicy.fromSettings` 测试、`docs/SOURCES.md` |
| 歌词：LRC 解析/格式化/偏移/去时间戳/繁简转换 | done | `:core:lyrics` 22 用例 |
| `.lrc` 侧文件：命名模式、模板、编码、读写 | done | `LyricsSidecarTest` |
| 批量编辑：11 字段、覆盖/补缺、前台服务 | done | `BatchOpsTest`、`BatchRunnerTest` |
| 文件操作：重命名/删除/分享/媒体库刷新 | done | 代码路径 + 契约校验（无真机验证） |
| 设置：48 项、Schema 单源渲染 | done | `SettingsSchemaTest` |
| 兼容：组件名、intent-filter、16 探针矩阵 | done | `docs/MANIFEST_CONTRACT.json`、`docs/INTENT_MATRIX.md` |
| 多选、书签/抽屉、列表面板密度、字符集选择、从文件名补全 | todo | 未实现，列为后续工作 |
| 捐赠 | dropped | 保留占位 Activity 以免显式调用崩溃 |


## 1. Library browsing

| # | Capability | Milestone | Status | Proof |
|---|---|---|---|---|
| 1.1 | Song / Album / Artist tabs | M2 | todo | instrumented list test |
| 1.2 | Sorting: song by title/album/artist/year/date; album by album/artist/date; artist by artist/date | M2 | todo | unit test on comparators |
| 1.3 | List covers: embedded / album art / folder image | M2 | todo | manual matrix |
| 1.4 | Bookmarks (add/remove/rename), drawer | M5 | todo | instrumented test |
| 1.5 | Multi-select: select all / clear / range | M5 | todo | instrumented test |
| 1.6 | Open folder, "find all music" | M2 | todo | manual |

## 2. Tag editing

| # | Capability | Milestone | Status | Proof |
|---|---|---|---|---|
| 2.1 | 12 text fields: title, artist, album, albumartist, genre, composer, track, trackstr, disc, discstr, year, comment | M2 | todo | round-trip unit test |
| 2.2 | ID3v2.3 / ID3v2.4 selection (`id3v2_mode`) | M2 | todo | byte-level fixture test |
| 2.3 | Reset tags, clear all tags, clear comment only | M2 | todo | unit test |
| 2.4 | Fill tags from file name (`Related to the file names`) | M2 | todo | unit test on parser |
| 2.5 | Character set selection | M3 | todo | unit test |
| 2.6 | Tag history (SQLite `tagshistory`) | M5 | todo | instrumented test |
| 2.7 | Property view: file attributes and MediaStore attributes | M5 | todo | manual |

## 2a. Intent resolution

| Row | Capability | Status |
|---|---|---|
| 2a.1 | Every historical intent still resolves to the component it did in 1.2.5.2 | ✅ verified in CI (`tools/intent_matrix.py` against the built APK) |
| 2a.2 | Negative cases: non-audio documents and `*/*`-only senders of typed formats resolve to nothing | ✅ asserted |
| 2a.3 | Explicit-component calls, `android.app.searchable` meta-data and the `SearchSugg` authority | ✅ asserted |
| 2a.4 | Same probes on a real device (`adb`) | ⏳ `scripts/device-intent-matrix.sh`, needs an emulator |

## 3. Cover art

| # | Capability | Milestone | Status | Proof |
|---|---|---|---|---|
| 3.1 | Read / replace / remove embedded cover | M2 | todo | round-trip test |
| 3.2 | Size + format limits for embedded pictures | M2 | todo | unit test |
| 3.3 | Force replace album art | M2 | todo | unit test |
| 3.4 | Search cover art online | M4 | todo | provider test |
| 3.5 | Crop / rotate / flip before saving | M6 | todo | manual |

## 4. Lyrics

| # | Capability | Milestone | Status | Proof |
|---|---|---|---|---|
| 4.1 | Read / write lyrics in tags | M2 | ✅ done | round-trip test |
| 4.2 | Import from / export to `.lrc` | M2 | todo | round-trip test |
| 4.3 | Reformat timetags, remove timetags, adjust offset | M3 | todo | fixture test |
| 4.4 | Delete blank text lines, delete head tags | M3 | todo | fixture test |
| 4.5 | Bilingual lyrics: download translations, merge order, connectors | M4 | todo | provider fixture |
| 4.6 | Traditional ↔ Simplified conversion (tags and file names) | M3 | todo | round-trip test |
| 4.7 | `.lrc` encoding, directory and file-name modes | M2 | todo | unit test |

## 5. Online providers

| # | Provider | Endpoints | Milestone | Status |
|---|---|---|---|---|
| 5.1 | NetEase Cloud Music | `cloudsearch/pc`, `v3/song/detail`, `song/lyric` + weapi crypto | M4 | ✅ done (fixtures; live endpoint unverified) |
| 5.2 | QQ Music | `musicu.fcg`, lyric fcgi (JSONP+base64+XML), `T002R800x800M000` cover | M4 | ✅ done |
| 5.3 | Kuwo | `www.kuwo.cn/api/www/search/searchMusicBykeyWord`, `m.kuwo.cn/newh5/singles/songinfoandlrc` | M4 | ✅ done (JSON endpoints; LRC rebuilt from lines) |
| 5.4 | Kugou | `mobilecdn` search, `krcs` candidate + `lyrics.kugou.com` LRC download | M4 | ✅ done (LRC, not KRC) |
| 5.5 | MusicBrainz | `/ws/2/recording`, `/ws/2/release` (+ required User-Agent) | M4 | ✅ done |
| 5.6 | VGMdb | album search + album page (HTML) | M4 | ✅ done (scraped; no JSON API exists) |
| 5.7 | iTunes Search | `/search` + `/lookup` (+ country), artwork size rewriting | M4 | ✅ done |
| 5.8 | Cover Art Archive | `front-500` per release id, with existence check | M4 | ✅ done |
| 5.9 | Last.fm | `track.search`, `track.getInfo` | M4 | ✅ done (API key injected, inert without one) |
| 5.10 | Per-source priority for combined tags / covers / lyrics | M4 | ✅ done (`MergePolicy`) | unit test |
| 5.11 | Result limit, search criteria | M4 | engine done (registry enforces limits); UI pending | manual |
| 5.12 | Provider health/diagnostics screen | M4 | failures recorded by the registry; screen pending | manual |


> **Verification note.** Provider behaviour is verified against recorded response fixtures
> (see `core/net/src/test/resources/providers`). The services themselves are not contacted
> by CI, so their current availability and field shapes are unverified; the providers
> tolerate missing or reordered fields and surface HTTP/parse failures instead of crashing.
## 6. Batch operations

| # | Capability | Milestone | Status | Proof |
|---|---|---|---|---|
| 6.1 | Overwrite 11 fields across a selection | M5 | ✅ done | `BatchOps` (11 fields, overwrite/fill-missing) + `BatchRunner` tests |
| 6.2 | Save each field to tag vs to `.lrc`/file | M5 | ⏳ per-field routing still open; tags are written today | pending |
| 6.3 | 14 utility actions (lyric cleanup, CHT/CHS, clear tags, cover save-as, …) | M5 | ⏳ lyric cleanup done (`processLyrics`); CHT/CHS and cover save-as open | partial |
| 6.4 | Rename files mode / change tags mode | M5 | ⏳ template engine done and tested (`BatchOps.rename`); the service does not rename files yet | partial |
| 6.5 | Foreground service with progress and cancellation | M5 | ✅ done (`BatchService`, cancel action, `BatchRunner` cancellation test) | pending (needs a device) |
| 6.6 | MediaStore refresh service | M5 | ⏳ every write-back already refreshes MediaStore (`DocumentWriter`); the standalone service is still a stub | partial |

## 7. Files and search

| # | Capability | Milestone | Status |
|---|---|---|---|
| 7.1 | Rename / delete / share (FileProvider) | M5 | ⏳ FileProvider is declared and unchanged; the UI actions are open |
| 7.2 | Local search screen | M5 | ✅ done (`SearchMediaActivity` + filters) |
| 7.3 | `SEARCH` intent + recent suggestions provider | M5 | ✅ intent handled; suggestions provider declared and covered by the intent matrix |
| 7.4 | Ignored directories, minimum duration, ignore MP4 videos | M2 | ✅ done (`MediaFilters`, 8 tests) |

## 8. Settings

**46 keys** are declared once in `SettingsSchema` and rendered from that declaration;
`docs/SETTINGS.md` is generated from the same source. Every key from the original
`pref_settings.xml` whose semantics this app keeps is stored under its original name; the
new keys (`write_id3v1`, `lastfm_api_key`, batch rename template, MediaStore rescan) are
additive. Storage is a typed repository rather than scattered `SharedPreferences` reads,
and the screen persists once when it leaves the foreground.

| Row | Capability | Status |
|---|---|---|
| 8.1 | Settings screen grouped by area, generated from the schema | ✅ done |
| 8.2 | 46 typed keys with defaults, round-tripped through storage | ✅ done (unit tested) |
| 8.3 | Reset to defaults | ✅ done |
| 8.4 | Original preference names preserved for a future import | ✅ done (asserted by test) |

## 9. Formats

Status legend: **read** = parsed and covered by a fixture test; **write** = editable.

| Container | Tag blocks | Read | Write |
|---|---|---|---|
| MP3 | ID3v1, ID3v2.3, ID3v2.4 | ✅ M1 (ID3v2.3 + APIC + USLT fixtures) | ✅ M2 (per-frame writes, APIC, USLT, ID3v1 companion) |
| FLAC | VorbisComment + PICTURE | ✅ M1 (13 properties + cover fixture) | ✅ M2 (XiphComment replace + PICTURE) |
| MP4/M4A | `ilst` (incl. `covr`) | ✅ M3 (minimal ftyp+moov fixture, 9 text atoms + trkn/disk + covr) | ✅ M3 |
| APE | APEv2 | ✅ M3 (synthetic MAC header + 12-item APEv2 tag) | ✅ M3 (text + Cover Art (Front)) |
| Ogg Vorbis / Opus | VorbisComment | ✅ M3 (hand-built Ogg pages with real CRCs) | ✅ M3 (text + METADATA_BLOCK_PICTURE) |
| WMA | ASF | engine support, fixture pending | engine support, fixture pending |
| WAV / AIFF | RIFF LIST-INFO / ID3 chunk | engine support, fixture pending | ID3 chunk only (TagLib cannot create one) |
| AAC | ADTS + leading ID3v2 | engine support, fixture pending | engine support, fixture pending |
| TTA | ID3v2 / APEv2 | engine support, fixture pending | engine support, fixture pending |
| WavPack | APEv2 | engine support, fixture pending | engine support, fixture pending |
| Musepack | APEv2 / ID3v2 | engine support, fixture pending | engine support, fixture pending |
| DSF | trailing ID3v2 | engine support, fixture pending | engine support, fixture pending |
| DFF | `ID3 ` chunk (gated by setting) | engine support, fixture pending | engine support, fixture pending |

Engine-level facts established in M1 and enforced by the native job:

* `TagLib::FileRef` opens by content sniffing, so extension-less documents work.
* Container detection and tag classification use `dynamic_cast`; MPEG/FLAC return a
  `TagUnion` proxy from `tag()`, so their concrete tags come from `ID3v2Tag()` /
  `xiphComment()`. This is documented in the bridge because it silently yields
  "unknown" otherwise.
* Bit depth comes from the per-format `Properties` subclasses (FLAC/APE/ASF).
* ID3v2 reports `TRCK` as `"3/12"` while VorbisComment splits it; the bridge normalises
  both into `track` + `trackStr`.


## 10. Deliberately dropped

| Item | Reason |
|---|---|
| Donation screen (Alipay/WeChat) | not part of the product; `DonateActivity` shell only |
| Xiami provider | service shut down; only dead code in 1.2.5.2 |
| ViewLyrics provider | dead code in 1.2.5.2 |
| jsoup, Apache Commons, Edmodo Cropper, ColorPicker, SpeedDial, Brotli decoder | unused or replaced by platform/Compose equivalents |
| Request-parameter logging, debug-only probes | leaks request bodies into logcat |
