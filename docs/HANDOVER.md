# 交接文档

> 项目：`JunRutey/musictag-android` —— 音乐标签（`com.xjcheng.musictageditor` 1.2.5.2）的
> Android 重写版。
> 状态：功能完成，**进入打磨阶段**，不再规划大的功能更新。
> 当前发行版：`v1.1.9`（唯一保留的 release）；代码在 `main`。
> 本文件写给接手的人：先读这一份，再看 README 与 docs/ 下的专题文档。

---

## 1. 项目是什么

原应用没有源码。本项目从 APK 黑盒分析出发，用 Kotlin + Jetpack Compose + Miuix 重写，
并把「别人怎么调起这个 App」当作必须保留的契约：

- 包名、15 个 Activity、2 个 Service、2 个 Provider、intent-filter、Provider authority 全部保留；
- 另外提供现代实现：TagLib 2.0.2（JNI）、六个在线源（网易/QQ/酷我/iTunes/MusicBrainz/
  Cover Art Archive）、拼音索引、类型化图片（前封面/封底/艺术家）、`.lrc` 侧文件、应用内日志。

**判断标准**：如果改动会影响上面这些对外暴露的东西，先看 `docs/COMPAT.md` 与
`docs/MANIFEST_CONTRACT.json`，再动手。

## 2. 五分钟上手

```bash
git clone https://github.com/JunRutey/musictag-android
cd musictag-android

./gradlew :app:assembleDebug        # 调试包，包名带 .debug，用调试签名
./gradlew test                      # 单元测试
python3 tools/selfcheck.py          # 本地自检
```

装包验证：取 CI 的 `app-debug` artifact（每次推送都会生成），或本地 `assembleDebug` 的产物。
它与发行版可以并存（包名 `com.xjcheng.musictageditor.debug`）。

发版：见第 6 节，**不要**在没确认的情况下打 tag。

## 3. 代码地图

| 路径 | 职责 | 什么时候改它 |
|---|---|---|
| `app/` | Activity、Compose 界面、设置、日志、文件操作 | 绝大多数 UI/行为改动 |
| `app/ui/library` | 曲库三个 Tab、分组、索引、排序、行渲染 | 曲库相关 |
| `app/ui/editor` | 编辑器（封面卡 + 填充式字段 + 保存/搜索） | 编辑相关 |
| `app/ui/search` | 在线补全界面与结果封面 | 搜索体验 |
| `app/ui/settings` | Schema 驱动的设置页 + 自定义外观区 | 设置项 |
| `app/settings` | `SettingsSchema`（唯一事实来源）、`Settings`、`Appearance` | 增删设置项 |
| `app/logging` | `AppLog`（文件 + logcat + 崩溃）、`LogExporter`（导出到下载） | 日志 |
| `app/io` | `DocumentSource`（暂存）、`DocumentWriter`（写回）、`FileOps`、`LyricsSidecar`、`IgnoreDirectory` | 文件读写 |
| `core/tag` | 标签模型、`TagLibEngine`、`NativeTagBridge` | 标签语义 |
| `native/taglib` | `jni_bridge.cpp` + CMake + TagLib | JNI、容器支持 |
| `core/net` | Provider 与合并策略、OkHttp 传输、离线 fixture 测试 | 在线源 |
| `core/lyrics` | LRC 解析/格式化 | 歌词处理 |
| `tools/` | `selfcheck.py`、`manifest_contract.py`、`intent_matrix.py`、fixture 生成 | 校验规则 |
| `docs/` | 契约、设置、来源、日志、发布、UI、功能对照 | 文档与规则同步 |

数据流（打开一首歌）：`SearchMediaActivity` 行点击 → `Intent` 带 `SongDetailActivity.EXTRA_URI`
→ 编辑器 `DocumentSource` 把 `content://` 暂存到 cache → `TagLibEngine.read` → 编辑 →
`engine.write`（必要时 `MediaStore.createWriteRequest` 申请授权）→ `DocumentWriter` 写回 →
`LyricsSidecar` 可选写 `.lrc` → `RefreshMediaStoreService` 刷新媒体库。

## 4. 关键决策与踩过的坑（务必保留）

1. **工具链被钉死**：Kotlin 2.2.0 / AGP 8.10.1 / Gradle 8.13 / NDK 27.0.12077973 /
   compileSdk 35 / **minSdk 24**。Miuix 只能停在 `0.7.2`（再新就是 Kotlin 2.3/2.4 元数据），
   Compose BOM 只能到 `2025.10.00`。升级任何一项都要先确认 Miuix 与新 Kotlin 的兼容矩阵。
2. **设置键的类型一旦发布就很难改**：1.1.7 把 `duration_of_ignore_medias` 从 `Int` 改成
   字符串选择项，旧装置里存的是整数，`getString` 抛 `ClassCastException`，而读取发生在
   `Application.onCreate` → 启动即闪退。现在读取统一走 `preferences.all` 再交给 `Settings`
   做类型转换（`SettingsRepository.load`），并有 `oldTypesStillReadAfterAChoiceConversion`
   兜底测试。**以后改任何已发布键的类型，必须保留这种容错。**
3. **不要给「自己 App 内的 MediaStore URI」加 `FLAG_GRANT_*`**：1.1.4 及更早，曲库把
   `content://media/...` 放进 `data` 并加 grant 标志，`startActivity` 阶段就被
   `UriGrantsManagerService` 拒绝（`UID ... does not have permission`）→ 点歌闪退。现在
   内部跳转只用 `SongDetailActivity.EXTRA_URI` 字符串 extra；对外 `ACTION_VIEW` 不变。
4. **写 MediaStore 需要用户授权**：`DocumentWriter` 遇 `SecurityException` 返回
   `Result.NeedsPermission`，编辑器用 `MediaStore.createWriteRequest` 弹一次系统框后重试同一
   次保存；Android 10 另开 `requestLegacyExternalStorage`。
5. **Provider authority 由 `${applicationId}` 派生**（manifest 里是占位符，不是字面量）：
   发行包解析成契约值 `com.xjcheng.musictageditor.SearchSugg` / `com.xjcheng.musictageditor.provider`，
   调试包则自动变成 `.debug` 版本，两个包才能并存（authority 全设备唯一，写死会
   `INSTALL_FAILED_CONFLICTING_PROVIDER`）。改这里要同时看 `src/debug/res/xml/searchable.xml`
   与 `SearchSuggProvider.AUTHORITY`（后者由 `BuildConfig.APPLICATION_ID` 派生）。
   `provider_paths.xml` 里既有契约要求的 `storage` 根，也有新增的 `files/logs`。
6. **主题切换必须是快照状态**：`Appearance.mode` 是 Compose 状态，`MusicTagTheme` 每次直接
   按它选色（`lightColorScheme`/`darkColorScheme`/`platformDynamicColors`）。曾经用
   `ThemeController` + `remember` + 从存储重读，导致「点了不切换」「动态取色无效」。
   **改设置时请用内存里的新值 `Appearance.update(...)`，不要 `refresh()` 重读存储**——存储只
   在 `onPause` 写盘，重读会拿到旧值。
7. **契约校验对象是发行包**：debug 带 `.debug` 后缀，包名与契约不符；`ci.yml` 用
   `app-release-unsigned.apk` 校验契约与 intent 矩阵，`release.yml` 校验签名后的发行包。
8. **JNI 与 Kotlin 必须成对**：`tools/selfcheck.py` 会检查 `jni_bridge.cpp` 里每个
   `Java_..._NativeTagBridge_xxx` 都有对应的 `external fun`。改 native 签名时两边一起改。
9. **硬编码文案闸门**：`selfcheck.py` 禁止 `app/src/main/java` 里出现英文 `Text("...")`；
   所有用户可见文案走 `R.string`，默认中文、`values-en` 镜像，`StringResourceParityTest`
   校验键集合与格式参数。
10. **发行版曾被污染**：早期把未完成功能与已知闪退的构建发进了 release（1.1.0–1.1.8），
    2026-10-31 已删除这些发行页与 tag，只保留验证通过的 `v1.1.9`。**以后只发经过确认的包。**

## 5. 验证体系

| 层 | 位置 | 覆盖 |
|---|---|---|
| 单元测试 | `app/src/test`、`core/*/src/test` | 设置 Schema、拼音、分组、搜索页、来源解析（离线 fixture）、LRC、文件命名、日志导出、旧类型迁移 |
| Native 真实读写 | `core/tag` + 第二个 CI job（host 构建同一份 TagLib 源码） | MP3/FLAC/Ogg/APE/M4A 读写与图片类型；`MUSICTAG_HOST_LIB` 指向产物 |
| Lint | `:app:lintDebug`，`abortOnError = true` | API 级别、可访问性等 |
| 契约 | `tools/manifest_contract.py`（对发行包） | 包名、组件、META-DATA、intent-filter |
| intent 矩阵 | `tools/intent_matrix.py` | 16 个探针的解析结果 |
| 自检 | `tools/selfcheck.py` | 资源键、契约文件、JNI 配对、硬编码文案、fixture |

**没有做的事**：没有真机/模拟器自动化测试，没有性能基准，没有无障碍审计。所以
`docs/UI.md` 里列了「未验证项」，改动 UI 时请依靠真机自测。

## 6. 发版流程

1. 改 `gradle.properties` 的 `versionCode` / `versionName`（例如 `12` / `1.1.10`）。
2. 在 `CHANGELOG.md` 顶部写 `## [1.1.10] - YYYY-MM-DD` 小节（**中文**；发行说明由 workflow
   从这里抽取，不要再手写英文）。
3. 提交、推送，等 CI 全绿；用 `app-debug` 包在真机确认。
4. 打 tag 并推送：
   ```bash
   git tag -a v1.1.10 -m "Music Tag 1.1.10"
   git push origin v1.1.10
   ```
   `release.yml` 会：签名 → `apksigner verify` → 契约校验 → 从 CHANGELOG 生成中文发行说明 →
   上传 APK。
5. 校验产物（本地）：
   ```bash
   apksigner verify --print-certs musictag-1.1.10.apk
   aapt dump badging musictag-1.1.10.apk | head -1
   ```
6. 把体积与 SHA-256 补回 CHANGELOG 的该版本小节。

签名密钥在私有仓库 `JunRutey/musictag-signing`，GitHub Secrets 已配置
（`SIGNING_KEYSTORE_BASE64`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、
`SIGNING_KEY_PASSWORD`）。证书指纹：`51:48:5C:4E:5E:A8:51:00:5C:D7:20:51:DF:BF:0A:C8:CE:64:F6:B2:11:59:9B:55:6E:70:4C:6A:D7:D7:9D:4D`。

## 7. 已知限制与待打磨清单

按优先级（都是「锦上添花」，不是阻塞项）：

1. **批量自动匹配**：曲库多选 → 按文件信息逐首在线匹配 → 只写回非空字段（后台服务 + 通知）。
   目前只留了 `BatchEditSongsDetail` 的占位与说明。
2. **艺术家/专辑二级页**：已实现为曲库内的独立详情页（`GroupDetailScreen`，带返回、按专辑+
   音轨号排序）。历史 `ArtistActivity` / `AlbumActivity` 仍是兼容空壳，不参与这条路径。
3. **行内 `⋮` 菜单**：文件动作目前放在编辑器顶栏（分享/删除/重命名），曲库行内没有菜单。
4. **`.lrc` 目录自定义**：固定写在音频同目录；`custom` 目录需要 SAF 写入支持。
5. **在线源数量与质量**：酷狗、Apple Music 已移除；QQ/酷我解析依赖网页接口，长期可能失效。
6. **无真机自动化**：索引拖动、封面解码、深色对比度都靠人工验证。
7. **`core/net` 没有日志注入**：全量日志目前只到「哪个来源失败」，看不到单次 HTTP 细节。

## 8. 排查手册

- **启动/操作闪退**：设置 → 关于 → 导出到「下载」；或 `adb logcat -s "MusicTag/*:*"`。
  崩溃在日志里是 `E Crash:` 段，带线程名和完整堆栈。
- **设置改了没效果**：先确认该键在 `SettingsSchema.specs` 里，且读取方用的是内存值而不是
  重新读存储（第 4 节第 6 条）。
- **CI 契约失败**：`tools/manifest_contract.py` 会输出缺了哪个组件/哪条 filter；对照
  `docs/MANIFEST_CONTRACT.json`，不要为了过检去改契约 JSON。
- **native 构建失败**：先看 `native/taglib/CMakeLists.txt` 的 TagLib 版本与 ABI 过滤，
  再看 `jni_bridge.cpp` 是否与 `NativeTagBridge.kt` 成对。

## 9. 交接清单

接手后建议按这个顺序确认：

- [ ] 能本地跑 `./gradlew :app:assembleDebug` 并装到手机（包名以 `.debug` 结尾）
- [ ] 能读懂 `SettingsSchema.kt` 与 `SettingsScreen.kt` 的对应关系
- [ ] 知道 `docs/RELEASING.md` 的双轨流程与「先确认再发版」
- [ ] 知道签名密钥在私有仓库，不上传到本仓库
- [ ] 知道日志开关与导出路径，能独立抓一次崩溃日志
- [ ] 读过 `docs/COMPAT.md`，明白哪些东西不能动
