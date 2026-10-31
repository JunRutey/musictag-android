# Music Tag for Android（重写版）

[![CI](https://github.com/JunRutey/musictag-android/actions/workflows/ci.yml/badge.svg)](https://github.com/JunRutey/musictag-android/actions/workflows/ci.yml)
[![Release](https://github.com/JunRutey/musictag-android/actions/workflows/release.yml/badge.svg)](https://github.com/JunRutey/musictag-android/actions/workflows/release.yml)

**音乐标签 / Music Tag** 的 Android 重写版。原应用最后版本是 `com.xjcheng.musictageditor`
1.2.5.2，没有公开源码；本项目从 APK 的黑盒分析出发，用 Kotlin + Jetpack Compose + Miuix
重新实现，同时保留原版的**兼容契约**（包名、组件名、intent-filter、Provider authority），
让其他 App 仍然可以照旧调起它。

> English summary: a from-scratch, compatibility-preserving rewrite of the Android tag editor
> *Music Tag* (`com.xjcheng.musictageditor` 1.2.5.2), built with Compose + Miuix and upstream
> TagLib via JNI. Feature work is complete; the project is now in a polish phase.

**状态：功能完成，进入打磨阶段。** 后续只做细节优化、缺陷修复和兼容性维护，不再有大的功能
变更。当前发行版 **v1.1.10**，代码在 `main`，每次推送由 CI 全量验证。

## 功能一览

| 模块 | 能力 |
|---|---|
| 曲库 | 歌曲 / 艺术家 / 专辑三个 Tab，各自独立：歌曲恒为全库（只受搜索框影响），艺术家与专辑点开是带返回的独立详情页；拼音 A–Z 分组与可拖动索引（带字母气泡），封面、格式角标、时长与码率，标题/艺术家/专辑/时长排序，常驻筛选与排序条 |
| 标签编辑 | 标题、艺术家、专辑、专辑艺术家、流派、作曲、音轨、碟号、年份、注释、歌词；ID3v1 与 ID3v2.3/2.4 选项；封面与「艺术家图片」两个图片槽（类型化图片，MP4/ASF 单图降级） |
| 标签引擎 | 上游 [TagLib 2.0.2](https://taglib.org/) 经 JNI 调用，支持 MP3 / FLAC / Ogg / APE / M4A 的读写与图片类型 |
| 在线补全 | 网易云、QQ、酷我、iTunes、MusicBrainz（+ Cover Art Archive）；进入即自动搜索，结果带来源封面，点选后预览再应用 |
| 歌词 | LRC 解析/格式化/时间戳处理/繁简转换；可同时写 `.lrc` 侧文件（编码与文件名可选） |
| 文件操作 | 重命名（保留扩展名）、删除、分享、媒体库刷新；MediaStore 写入需要授权时会弹系统确认并自动重试 |
| 设置 | 23 项，单源 Schema 渲染；可选项用选择面板而不是手填；主题颜色（跟随系统/白色/深色）与动态取色（Monet） |
| 日志 | 应用内日志 `files/logs/app.log`（普通 512 KiB / 全量 4 MiB 轮转），崩溃带完整堆栈；设置里可导出到「下载」或走系统分享，也能用 logcat 抓 `MusicTag/*` |

## 下载与安装

从 [最新发行版](https://github.com/JunRutey/musictag-android/releases/latest) 下载：

- 发行包由仓库密钥签名，证书 SHA-256
  `51485c4e5ea851005cd72051dfbf0ac8ce64f6b211599b556e704c6ad7d79d4d`。
- 安装前先卸载原版 1.2.5.2：签名不同，Android 不允许直接覆盖。标签在音频文件里不受影响，
  应用设置与历史不会继承。
- 想并行试用新构建：CI 每次推送都会产出调试包 `app-debug`，包名是
  `com.xjcheng.musictageditor.debug`、用调试密钥签名，可与发行版共存。

构建与发版流程见 [docs/RELEASING.md](docs/RELEASING.md)。

## 兼容性

| 项目 | 值 |
|---|---|
| applicationId | `com.xjcheng.musictageditor` |
| 入口 | `MainActivity`、`SongDetailActivity`、`activity.SearchActivity`（导出） |
| 文档 intent | `VIEW` 覆盖 20 种音频 MIME，另有 `*/*` + 24 条 `pathPattern` |
| 分享 | `SEND`（`*/*`） |
| 搜索 | `SEARCH` + `android.app.searchable` |
| Provider | `com.xjcheng.musictageditor.SearchSugg`、`com.xjcheng.musictageditor.provider` |

契约是可执行的，不只是文档：

- `app/src/test/.../ManifestContractTest.kt`：针对源 manifest 的 JVM 测试；
- `tools/manifest_contract.py`：对**构建出的发行包**合并后 manifest 做比对；
- `tools/intent_matrix.py`：16 个 intent 探针，验证解析结果仍然落在原组件上；
- 细节与「不承诺什么」见 [docs/COMPAT.md](docs/COMPAT.md)。

## 项目结构

```
app/                 UI、Activity、设置、日志、文件操作
  ui/library        曲库（歌曲/艺术家/专辑）
  ui/editor         标签编辑器
  ui/search         在线补全
  ui/settings       Schema 驱动的设置页
core/tag            Kotlin 侧标签模型 + JNI 桥（native/taglib 为 CMake + TagLib）
core/net            各音乐站 Provider、合并策略、OkHttp 传输、离线 fixture 测试
core/lyrics         LRC 解析与格式化
native/taglib/      jni_bridge.cpp + TagLib FetchContent 构建
tools/              selfcheck、manifest 契约、intent 矩阵、fixture 生成
docs/               契约、来源、设置、UI、日志、发布、交接文档
```

## 构建与验证

CI 是唯一权威构建（`ubuntu-latest`、JDK 21、Gradle 8.13）。本地需要同一套工具链
（Kotlin 2.2.0、AGP 8.10.1、NDK 27.0.12077973、compileSdk 35）。

```bash
./gradlew :app:assembleDebug          # 调试包：com.xjcheng.musictageditor.debug，1.1.10-debug
./gradlew test                        # 单元测试（含 native 引擎的真实读写，需 MUSICTAG_HOST_LIB）
python3 tools/selfcheck.py            # 本地自检：资源、契约文件、JNI 配对、硬编码文案、fixture
./gradlew :app:lintDebug              # Lint（CI 会跑，abortOnError）
```

CI 每次推送做这些事：Gradle 构建 → 单元测试 → Lint → 契约校验（针对发行包）→ intent 矩阵
→ 主机侧 TagLib 真实读写测试（第二个 job，构建同一份 native 源码并在 host 上跑）。

## 文档

| 文档 | 内容 |
|---|---|
| [docs/HANDOVER.md](docs/HANDOVER.md) | **交接文档**：现状、代码地图、坑与决策、待打磨清单 |
| [docs/RELEASING.md](docs/RELEASING.md) | 双轨构建（debug/发行）、发版步骤、发行版清理记录 |
| [docs/SETTINGS.md](docs/SETTINGS.md) | 23 项设置、已删除的键 |
| [docs/SOURCES.md](docs/SOURCES.md) | 在线源、端点、优先级、移除原因 |
| [docs/LOGGING.md](docs/LOGGING.md) | 日志位置、导出方式、adb/logcat 命令 |
| [docs/COMPAT.md](docs/COMPAT.md) | 完整兼容契约与不承诺项 |
| [docs/UI.md](docs/UI.md) | 界面结构、未验证项 |
| [docs/PARITY.md](docs/PARITY.md) | 与原版的功能对照 |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更（中文） |

## 与原版的差异

有意移除或改为不做：

- 下线的在线源（虾米、ViewLyrics、VGMdb、Last.fm、酷狗、Apple Music）
- 捐赠页（`DonateActivity` 保留为空壳，仅为让显式调用仍能解析）
- 手动批量编辑（改为保留入口占位；按文件自动匹配尚未实现）
- jsoup、Commons、图片裁剪库、颜色选择器、多个重复 HTTP 栈、请求参数调试日志
- 任何统计、崩溃上报、遥测

## 许可证

本仓库代码为 Apache-2.0，见 [LICENSE](LICENSE)。第三方组件见 [NOTICE](NOTICE)；TagLib 以
MPL-1.1 使用，保持其文件为 MPL，同时允许本项目继续以 Apache-2.0 发布。
