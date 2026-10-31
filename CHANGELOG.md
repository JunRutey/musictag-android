# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.8] - 2026-10-31

### 修复

* 启动闪退：`duration_of_ignore_medias` 在 1.1.6 是 `Int`，1.1.7 变成选择项字符串，
  `SettingsRepository.load()` 仍按 `getString` 读取，旧安装里存的是整数，于是抛
  `ClassCastException`，而它在 `Application.onCreate` 里执行，直接把进程带走。现在读取
  用 `preferences.all` 取原始值再由 `Settings` 做类型转换，旧类型不会崩。
* `MusicTagApp.onCreate` 的初始化加了兜底：日志或外观刷新失败不再影响启动。
* 新增 `oldTypesStillReadAfterAChoiceConversion` 覆盖这次的类型迁移。

## [1.1.7] - 2026-10-31

### 新增

* 设置项改为「点选」而不是手填：`.lrc` 编码（UTF-8/GBK）、`.lrc` 文件名（同名/艺术家-标题）、
  忽略时长（不启用/15/30/60 秒/2 分钟/5 分钟，默认 60 秒）、主题颜色（跟随系统/白色/深色）
  都变成底部选择面板。
* 「忽略的目录」改为系统文件夹选择器（OpenDocumentTree），保存相对路径并保留授权，可一键清除。
* 外观：`theme_mode` + `dynamic_color` 映射到 Miuix 的 `ThemeController`，支持
  System/Light/Dark 与三种 Monet 动态取色（Android 12+），切换即时生效。
* 统一输入观感：编辑器字段、设置里的文本框、搜索的三个输入行都用加深的圆角底，
  与背景区分；编辑器「保存」按钮左移 12dp。

### 移除

* 手动批量相关的 2 个设置（功能本身已移除）、`.lrc` 目录模式与文件名模板（固定同目录 +
  可选文件名）、旧的 `dark_theme`（被 `theme_mode` 取代）。设置项 25 → 22。
## [1.1.6] - 2026-10-31

已发布：https://github.com/JunRutey/musictag-android/releases/tag/v1.1.6

* APK：`musictag-1.1.6.apk`（9,636,893 字节，versionName 1.1.6，`apksigner verify` 退出码 0）。
* APK SHA-256：`fe87c15a3f1320f20a3f9c42c74bb2b0d059010ffc39c310d5d96c6afddcc3a4`。


### 新增

* 编辑页：右上角改为填充式「保存」按钮；右下角蓝色在线查找按钮上移到 96dp，不再压住最后一个字段。
* 在线搜索：从编辑页进入时自动搜索，用户只需点选结果；结果行左侧显示来源封面（44dp，
  带 LRU 缓存），不再只有文字。
* 保存到 MediaStore 文件：`DocumentWriter` 遇到 `SecurityException` 时返回「需要授权」，
  编辑页通过 `MediaStore.createWriteRequest` 弹一次系统授权框，允许后自动重试同一次保存。

### 移除

* 酷狗音乐与 Apple Music 两个「一直没响应」的来源：不再注册进应用、不在合并顺序里；
  实现与离线测试仍留在 `core/net`。随之删除 3 个 `apple_*` 设置（设置项 28 → 25）。

## [1.1.5] - 2026-10-31

已发布：https://github.com/JunRutey/musictag-android/releases/tag/v1.1.5

* APK：`musictag-1.1.5.apk`（9,652,608 字节，versionName 1.1.5，`apksigner verify` 退出码 0，
  证书 SHA-256 `51485c4e5ea851005cd72051dfbf0ac8ce64f6b211599b556e704c6ad7d79d4d`）。
* APK SHA-256：`04e7d2ac1625dcb16075917a715094a3b03f7bea075fbf2d1aef5c0496d92029`。


### 修复

* 点歌闪退的根因：曲库把 `content://media/...` 放进 intent 的 data 并加了
  `FLAG_GRANT_READ/WRITE_URI_PERMISSION`，系统在 `startActivity` 时校验发送方能否授予该
  URI，MediaStore 的行我们无权授予，于是抛 `SecurityException`。现在改为纯字符串 extra
  (`SongDetailActivity.EXTRA_URI`) 且不加任何 grant 标志；外部 `ACTION_VIEW` 调用不变。
* 分享 MediaStore 文件时若系统拒绝授权，会自动去掉 grant 标志重试。
* 「导出日志」改用 manifest 里真实存在的 FileProvider authority (`.provider`)，之前写成
  `.fileprovider`，所以分享面板一直打不开；`provider_paths.xml` 增加 `files/logs` 根。
* 移除 `MANAGE_EXTERNAL_STORAGE`（代码从未使用，且属于商店政策风险）。

### 新增

* 首次启动一次性申请所需权限：`READ_MEDIA_AUDIO`（旧版 `READ_EXTERNAL_STORAGE`）、
  Android 9 及以下的 `WRITE_EXTERNAL_STORAGE`、Android 13+ 的 `POST_NOTIFICATIONS`；
  记录已询问标记，不会每次启动都弹。
* 日志与主题偏好在 `MusicTagApp` 初始化，外部 VIEW/SEND 直接进入也不会漏。

## [1.1.4] - 2026-10-31

### 新增

* 设置 → 关于新增「导出到「下载」」：把日志写成 Download/musictag-log-<时间>.txt，
  不依赖系统分享面板；成功后显示保存路径，失败显示原因。
  Android 10+ 走 MediaStore（带 IS_PENDING），旧版本写公共 Download 目录，
  没有存储权限时退回应用外部目录。
* docs/LOGGING.md：日志位置、应用内导出、adb/logcat 命令、无线调试步骤。

## [1.1.3] - 2026-10-31

已发布：https://github.com/JunRutey/musictag-android/releases/tag/v1.1.3

* APK：`musictag-1.1.3.apk`（9,651,648 字节，versionName 1.1.3，`apksigner verify` 退出码 0，
  证书 SHA-256 `51485c4e5ea851005cd72051dfbf0ac8ce64f6b211599b556e704c6ad7d79d4d`）。
* APK SHA-256：`6e3a23edbead178c5fed6a6e0d2bc61162ae24ac72f0203cef9f79912c60a1e8`。

### 新增

* 应用内详细日志：写入 iles/logs/app.log（512 KiB 轮转，保留一份归档），
  同时镜像到 logcat 的 MusicTag/<tag>；未捕获异常会在进程结束前写入完整堆栈。
* 设置 → 关于新增「导出日志」（系统分享面板，摘要显示当前日志大小）与「清空日志」；
  FileProvider 只暴露 iles/logs。
* 编辑器会记录打开、读取、保存、重命名、删除、分享以及所有失败原因。

### 修复

* 深色主题可以手动切换了：dark_theme 改为快照状态（Appearance），切换后所有界面立刻重绘；
  之前的值被 
emember 缓存，切了也不会刷新。

## [1.1.2] - 2026-10-31

已发布：https://github.com/JunRutey/musictag-android/releases/tag/v1.1.2

* APK：`musictag-1.1.2.apk`（9,633,047 字节，versionName 1.1.2，`apksigner verify` 退出码 0，
  证书 SHA-256 `51485c4e5ea851005cd72051dfbf0ac8ce64f6b211599b556e704c6ad7d79d4d`）。
* APK SHA-256：`11e5c601cc6cc24429ed4255b6ea68d0dc7126d129f4d5e66c1193cf9a756996`。

### 修复

* 点击歌曲不再闪退：读取与写入标签都加了异常兜底，失败时在编辑页显示原因（读取标签失败：…）
  并在 logcat 以 MusicTagEditor 记录，而不是让应用退出。
* 首字母索引改为启动器式滑动：在 A–Z/# 竖条上拖动即按手指位置跳转，
  并显示 56dp 的字母气泡；单击仍然只跳一次。歌曲、艺术家、专辑三个列表共用。

## [1.1.1] - 2026-10-31

已发布：https://github.com/JunRutey/musictag-android/releases/tag/v1.1.1

* APK：`musictag-1.1.1.apk`（9,632,119 字节，versionCode 2026103018、versionName 1.1.1，
  CI 用既有密钥签名；`apksigner verify` 退出码 0，证书 SHA-256
  `51485c4e5ea851005cd72051dfbf0ac8ce64f6b211599b556e704c6ad7d79d4d`）。
* APK SHA-256：`8ff7efb9d7c8df8b5478a811c946ffc776c9631a5db2196cb142ea4d839797c5`。

### 移除

* 手动批量编辑：整体删除字段编辑页与曲库入口（注册的 `BatchEditSongsDetail` 保留为占位，
  类名与 `EXTRA_URIS` 不变）。批量自动匹配留待下一版。
* 12 个“改了没反应”的设置项：三个来源优先级字符串、`web_search_criteria`、主色、强调色、
  动画、黑主题、`tab_items`、手动批量开关、媒体库扫描、批量常亮。设置从 48 项精简到 28 项。
* 设置页右上角的重置按钮。
* 启动后的首页：`MainActivity` 直接进入曲库。
* 曲库顶栏的搜索/排序图标：搜索框与排序胶囊改为常驻（仅在歌曲页显示）。

### 修复

* 深色主题开关现在真的生效（主题此前只读系统状态，从不读 `dark_theme`）。
* 曲库点击歌曲进入编辑器：暂存文件名不再把中文标题全部抹成 `____`（加了 URI 哈希，避免两首
  中文名互相覆盖）；`content://` 读取增加文件描述符兜底；打不开时区分“没有权限 / 文件为空 /
  地址不可读 / 已移动或删除”并直接显示原因。
* 曲库标题跟随 Tab：`歌曲（120）`、`艺术家（12 项）`、`专辑（8 项）`。

### 新增

* 艺术家 / 专辑页：每行 48dp 封面 + 名称 + 数量，加入 A–Z/# 首字母分组与右侧索引条，
  与歌曲页一致。
* 编辑页按表单重排：左侧 132dp 封面卡（歌曲名 + 艺术家 + 封面/艺术家图片切换 + 分辨率胶囊 +
  编辑图片/移除）、字段改为圆角填充框（11sp 小标签 + 16sp 值）、右下角常驻 56dp 圆形“在线查找”
  按钮、`文件` 分组收纳重命名/分享/删除；不引入评级等本应用没有的功能。
* 设置页新增「关于」：项目仓库链接（点击打开 GitHub）与当前版本号。

## [1.1.0] - 2026-10-31

已发布：https://github.com/JunRutey/musictag-android/releases/tag/v1.1.0

* APK：`musictag-1.1.0.apk`（9,644,561 字节，versionCode 2026103016、versionName 1.1.0，
  由 CI 用既有密钥签名；`apksigner verify` 通过，证书 SHA-256
  `51485c4e5ea851005cd72051dfbf0ac8ce64f6b211599b556e704c6ad7d79d4d`）。
* APK SHA-256：`a758fd0e35ffbbee2c1dd1c0f5d5007485bee7a3b8838468690d23b04bcf031f`。

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

### 新增（M11 / M12 收尾）

* 编辑器：Miuix 顶栏（在线补全 / 分享 / 删除 / 保存）、十个行内字段、歌词多行编辑、
  封面与「艺术家图片」两个图片槽、MP3 的 ID3v1 / ID3v2.3 开关、底部重命名与删除动作。
* 设置、在线搜索、批量编辑全部换成 Miuix 行并中文化（48 项设置、来源筛选、只填充缺失项等）。
* 艺术家 / 专辑不再是占位页：共享 `GroupBrowserActivity` 按拼音分组，点击后把名字作为查询交给曲库。
* 标签引擎支持**类型化图片**：`readAll` 返回图片类型，新增 `writeAllWithPictures`；
  ID3v2/FLAC/Xiph/APE 可写前封面、封底、艺术家图，MP4 只保留前封面。
* 文件操作：`io/FileOps` 提供重命名（保留扩展名、MediaStore 与 SAF 两条路径）、删除、分享；
  `RefreshMediaStoreService` 变成真实的后台扫描服务。
* `.lrc` 侧文件：`io/LyricsSidecar` 支持 same / artist-title / custom 三种命名、`%t/%a/%l`
  模板、可配置编码，保存后按设置写侧文件，标签无歌词时回退读取。
* 中文优先的文案体系：133 个双语字符串键，`StringResourceParityTest` 校验键集合、
  中文默认值与格式参数；`selfcheck.py` 新增「禁止硬编码 UI 文案」闸门。
* 文档：`docs/SOURCES.md`（源与 Apple 令牌流程）、`docs/UI.md`（界面与未验证项）、
  `docs/PARITY.md` 增补 1.1.0 实况表。


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
