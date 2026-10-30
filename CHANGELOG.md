# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 未发布

界面改为 Miuix（MIUI/HyperOS 设计语言）并默认简体中文；搜索源收敛为四个中文源加英文兜底。
本节之后的条目保持英文，1.1.0 起的新条目用中文书写。

### 新增

* 曲库主界面：48dp 圆角封面（MediaStore `albumart`，内存 LRU）、格式角标（FLAC/MP3/M4A…）、
  `艺术家 - 专辑` 副标题、时长与 kbps 列、A–Z/# 拼音索引条、按标题/艺术家/专辑/时长排序、
  底部「歌曲 / 艺术家 / 专辑」三 Tab、搜索胶囊。排序与分组是纯函数并有单测
  （`LibraryEntriesTest`、`MediaGroupingTest`、`PinyinIndexTest`）。
* Apple Music 源：从网页播放器公开的 index 脚本解析短期开发者令牌（带 60 秒安全边界与
  401 强制刷新），查询 `amp-api.music.apple.com` 的区域化目录，按设置重写封面尺寸；
  录制 fixture 覆盖搜索、详情、令牌挑选与 401 重试。
* `tags_sources_*` 设置真正生效：`MergePolicy.fromSettings` 决定合并顺序，未知、重复与
  已下线的来源键被忽略而不是抛错。
* `MediaTrack` 增加文件名、专辑 id、文件大小、音轨、年份与码率；码率缺失时按
  `大小×8/时长` 推导。

### 变更

* 设计语言换成 Miuix 0.7.2（Apache-2.0），Compose BOM 随之从 2025.06.01 抬到 2025.10.00
  （Compose 1.9.3 / Material3 1.4.0）。Miuix 0.7.2 是仍能被钉住的 Kotlin 2.2.0 编译器读取的
  最后一个版本（0.8+ 使用 Kotlin 2.3 metadata）。
* `minSdk` 21 → 24：Miuix 0.7.2 声明 `minSdkVersion 24`，这是可用的最低门槛（0.6.1/0.5.2
  是 26，0.8.8 虽然 23 但需要更新的 Kotlin）。安装下限变为 Android 7.0，调用契约不变。
* 默认语言改为简体中文：`values/strings.xml` 是中文，`values-en/` 为英文，
  `StringResourceParityTest` 强制两套键完全一致、默认值含中文、格式参数匹配。
* 设置项文案改为中文，共 48 项（新增 `apple_region`、`apple_language`、`apple_cover_size`），
  `docs/SETTINGS.md` 同步重写。

### 移除

* VGMdb 与 Last.fm 源（Provider、API 客户端、fixture、测试以及 `lastfm_api_key` 设置）。
  旧配置里的 `vgmdb`/`lastfm` 键会被静默忽略。

## [1.0.0] - 2026-10-24

First signed release, built from CI commit `537fc1c` and signed with
`51:48:5C:4E:5E:A8:51:00:5C:D7:20:51:DF:BF:0A:C8:CE:64:F6:B2:11:59:9B:55:6E:70:4C:6A:D7:D7:9D:4D`.

Includes everything marked done in `docs/PARITY.md`: the TagLib-based engine (MP3, FLAC,
Ogg, APE, M4A read *and* write with artwork), the editor, nine online providers behind a
priority merge, the library with its filters, batch editing in a foreground service, the
46-key settings system, the lyric tooling, and the compatibility contract plus intent
matrix verified on every push.

Not in this release: rename/delete/share actions, `.lrc` side files, traditional/simplified
conversion, the remaining batch utility actions, and device-level verification.

## [Unreleased]

### Added (M5)

* `:core:lyrics`: LRC parse/format/reformat/offset/strip/blank-lines/head-tags/metadata and
  translation merging, 16 unit tests.
* Settings: 46 keys declared once in `SettingsSchema`, rendered from that declaration,
  stored under the original preference names where the semantics match, persisted once when
  the screen leaves the foreground. `docs/SETTINGS.md` is generated from the schema.
* Media library: `MediaLibrary` reads MediaStore (DATA or RELATIVE_PATH), `MediaFilters`
  applies minimum duration / ignored directories / MP4 videos / query (8 tests), and
  `SearchMediaActivity` is the real search screen the `SEARCH` intent opens.
* Batch editing: `BatchOps` (field merge semantics, rename templates, lyric pipeline, 12
  tests), `BatchRunner` (progress, cancellation, per-file failures, 7 tests), `BatchTagIo`,
  a real `BatchService` with a progress notification and cancel action, and
  `BatchEditSongsDetail` as its UI.
* The home screen became a usable entry point: library, open file, online search, settings.

### Added (M6, in progress)

* `tools/intent_matrix.py` resolves the historical intents the way Android does (action,
  categories, scheme, MIME, path patterns) against the built APK, asserts the winning
  component for 16 probes including negative cases, checks explicit-component targets, the
  searchable meta-data and the suggestions authority. CI runs it after the contract check
  and regenerates `docs/INTENT_MATRIX.md`.
* `scripts/device-intent-matrix.sh` replays the same probes through `adb`.

### Fixed during M5/M6

* `TagLibEngine` is an instance, not an object: the batch service had called it statically.
* `getSystemService(Class)` needs API 23 while minSdk is 21; notifications now go through
  `NotificationManagerCompat`, and notify failures no longer kill the service.
* The MP4 rule: MediaStore reports `audio/mp4` for both m4a files and the audio track of an
  mp4 video, so the extension decides (`src/.m4a` is audio, `src/.mp4`/`.m4v` are videos).
* Batch name sanitising turned control characters into underscores; they are dropped now.
* The intent matrix corrected three assumptions: `SEARCH` is discovered via meta-data (no
  DEFAULT category), there is no `EDIT` filter, and the `*/*` path filter intentionally
  lists only the formats whose MIME providers get wrong.

### Added (M4 backend, complete)

All nine providers from the original application are implemented behind one interface:

| Provider | Notes |
|---|---|
| NetEase | weapi-signed search/detail/lyric, lyrics translation |
| QQ Music | musicu.fcg search, JSONP+base64+XML lyric endpoint |
| Kuwo | JSON search endpoint, LRC rebuilt from structured lines |
| Kugou | search plus two-step LRC download (candidate by hash) |
| MusicBrainz | recording search and recording/release detail, required User-Agent |
| VGMdb | album search and album page scraped from HTML |
| iTunes | search and lookup, artwork size rewriting |
| Cover Art Archive | front cover per release, with existence check |
| Last.fm | search and track info; API key injected, inert without one |

`ProviderRegistry` runs a query across every tag provider (recording failures instead of
failing the search), collects candidates and merges them field by field with an explicit
`MergePolicy`: Chinese services win title/artist and lyrics, curated databases win
album-level fields, Cover Art Archive wins artwork, and blank values never win.

Three provider bugs were found by the tests: an empty artist credit used to become `""`
(which would have overwritten tags with blanks), artwork URLs without a size segment are
kept as-is, and a provider that ignores its result limit could flood the merged list, so
the registry enforces the limit itself.

### Verified (M3/M4 so far)

* `CI #59`: **54 tests, 0 failures, 0 skipped** (30 tag engine + 24 network).
  Formats read *and* written against generated fixtures: MP3 (ID3v2.3/2.4 + ID3v1),
  FLAC, Ogg Vorbis, APE (APEv2) and M4A (MP4 `ilst`), including artwork in every one.
* weapi is checked three ways: AES against NIST SP 800-38A vectors, the RSA step against a
  Python arbitrary-precision computation, and the two-layer composition by unwrapping it.
  The `163 key` marker round-trips and decrypts with the raw key bytes.

### Added (M3)

* One write path for every container TagLib supports, dispatched by tag family: ID3v2
  (MPEG/WAV/AIFF/DSF/DSDIFF/TrueAudio), Xiph (FLAC/Ogg Vorbis/Ogg Opus), APEv2
  (APE/WavPack/Musepack), MP4 and ASF.
* Cover art per family, and reading of Ogg/APEv2 artwork.
* Generated fixtures for Ogg (three pages with real CRCs) and M4A (ftyp+moov+ilst) and a
  synthetic Monkey's Audio file with a 12-item APEv2 tag.

### Added (M4, in progress)

* `:core:net`: weapi and `163 key` crypto, an `HttpTransport` abstraction with an OkHttp
  implementation, the provider model and the NetEase provider with recorded-response
  tests.

### Fixed during M3/M4

* `setProperties()` appended instead of replacing on ID3v2/FLAC (read back as
  "old / new") and dropped the comment; writes are now one frame per field.
* `MPEG::File::tag()`/`FLAC::File::tag()` return a `TagUnion` proxy, so casting them never
  matched: format showed "unknown" and MP3 artwork was invisible.
* FLAC keeps artwork in PICTURE blocks, not in the Vorbis comment.
* APEv2 cover keys are stored upper-cased, so the prefix match must be case-insensitive.
* The synthetic APE header packed bit depth/channels/sample rate with the wrong widths.
* `selfcheck` now strips comments, strings, raw strings and char literals before counting
  braces and parentheses (a key literal and a quote char literal both tripped it).

### Verified (M2)

* CI #39 green: 24 tag-engine tests, 0 failures, 0 skipped (read + write round trips).
* The editor compiles and ships in the debug APK; the write path is exercised by tests
  that copy fixtures, edit them, write, and read the result back.

### Added (M2)

* Compose editor: twelve fields, lyrics, artwork add/replace/remove, MP3 options, save.
* Write-back for content:// documents (mode 
wt) plus MediaScannerConnection
  refresh, and a settings repository for the MP3 write options.

### Fixed during M2 bring-up

* setProperties() does not replace on ID3v2/FLAC: the old frame survived and a second
  one was added (read back as "old / new"), and the comment was dropped. The write path
  now creates exactly one frame per field (diagnostic prints in the test log proved it).
* ID3v1::Tag is only forward-declared by mpegfile.h, so Tag::duplicate needed
  <mpeg/id3v1/id3v1tag.h> to see a complete type.

### Verified (M1)

* `CI #30` green with two jobs: the Android build (TagLib compiled for three ABIs, APK
  contains `lib/<abi>/libmusictag_jni.so`) and the native job (host build + tag tests).
* The tag-engine report shows **14 tests, 0 failures, 0 ignored** — the native tests ran
  against real TagLib rather than skipping.

### Fixed during M1 bring-up (each caught by CI, each now documented in-code)

* TagLib 2.x moved headers into per-format directories and includes siblings by bare
  name, so the bridge needs every header directory on its include path.
* `XiphComment` lives in `TagLib::Ogg`; `FLAC::Picture` exposes `data()`, not `picture()`.
* `-fvisibility=hidden` on the bridge duplicated TagLib's typeinfo and broke every
  `dynamic_cast`.
* `MPEG::File::tag()` and `FLAC::File::tag()` return a `TagUnion` proxy, so casting them
  yields nothing: the format showed "unknown" while tags parsed fine and MP3 covers were
  never found. Concrete tags now come from `ID3v2Tag()` / `xiphComment()`.
* ID3v2 keeps `TRCK` as `"3/12"` (VorbisComment splits it), so the numeric track field
  was unparsable until the bridge normalised both forms.

### Added (M1 — tag engine, read path)

* `native/taglib`: upstream TagLib 2.0.2 (MPL-1.1) pinned by tag and compiled for
  `arm64-v8a`, `armeabi-v7a`, `x86_64`; a ~400 line JNI bridge exposes one call that
  returns everything about a file (12 tag fields, lyrics, audio properties, cover art).
* `:core:tag`: pure-JVM `TagEngine`/`TagLibEngine` with a documented flat-array contract,
  its own base64 decoder (no `java.util.Base64`, which is API 26+) and no Android
  dependency, so it is unit-testable on the host JVM.
* Generated, license-free fixtures (`tools/gen_fixtures.py`): ID3v2.3+MPEG and
  FLAC+VorbisComment+PICTURE, asserted field by field.
* `:app` reads and displays the tags of a handed-over document, including `content://`
  documents staged through the new `DocumentSource`.
* CI: second job builds TagLib for the host and runs the tag tests; the main job installs
  the pinned NDK and ships the native library for three ABIs.
* `tools/selfcheck.py` now also guards the JNI↔Kotlin symbol pairing, native/cpp file
  hygiene and fixture drift.

### Verified

* First green CI run (`CI #14`): self-check, 10 contract unit tests, lint, `assembleDebug`,
  merged-manifest contract verification against the built APK, artifact upload.
* The built artifact reports `package=com.xjcheng.musictageditor`, `targetSdk=35` and the
  complete original component/filter surface (`tools/manifest_contract.py`: "manifest
  contract OK", only library-injected components reported as allowed extras).

### Fixed during bring-up

* KDoc containing the any-any MIME wildcard closed the comment block early (Kotlin
  reported "Expecting a top level declaration"); `selfcheck` now rejects it.
* AGP 8 forbids `package=` in the source manifest, so the contract test resolves
  component names from `CompatContract.PACKAGE` and asserts `applicationId`/`namespace`
  from `build.gradle.kts` instead.
* AAPT2 compiles manifest attribute values as Android strings and consumes a single
  backslash, so `.*\.ape` silently shipped as `.*.ape`. Every regex backslash in a
  `pathPattern` is now doubled in the source, the contract tool models the same
  unescaping, and `selfcheck` rejects a lone backslash.

### Added

* **M0 — scaffold and compatibility surface**
  * Application id `com.xjcheng.musictageditor` with the original component set
    (15 activities, 2 services, 2 providers) declared under their historical class names.
  * Intent contract: `VIEW` with 20 audio MIME types, `VIEW` with `*/*` + 24 path patterns,
    `SEND`, `SEARCH` + searchable metadata, `FileProvider` and search-suggestions authorities.
  * `SongDetailActivity` resolves and probes handed-over documents (`content://` and
    `file://`) and reports read/write capability instead of assuming a filesystem path.
  * Executable contract: `ManifestContractTest` (JVM) plus `tools/manifest_contract.py`
    which verifies the merged manifest of a built APK in CI.
  * GitHub Actions: build/test/lint, signed release on tags, manual emulator job, Dependabot.
  * Compose + Material 3 shell with placeholder screens for every registered activity.

### Changed

* `minSdk` 21 and `targetSdk` 35 (original: 16/28). Required for a modern UI and for
  platform-enforced `exported` declarations; no effect on the calling contract.

### Removed

* Donation screen and all payment links (the activity class survives as an empty shell).
* Xiami and ViewLyrics integrations, plus the previously bundled jsoup, Apache Commons,
  Edmodo Cropper, ColorPicker and SpeedDial libraries — none of them are used by the rewrite.
