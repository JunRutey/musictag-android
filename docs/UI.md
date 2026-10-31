# 界面实现与验证状态

## 设计语言

界面使用 [Miuix](https://github.com/compose-miuix-ui/miuix) 0.7.2（Apache-2.0）——MIUI/HyperOS
的 Compose 设计语言。主题入口在 `ui/theme/Theme.kt`：

```kotlin
MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
    MaterialTheme(colorScheme = materialColors, content = content)   // 迁移期过渡
}
```

Material 3 只留在 `activity.PlaceholderActivity`（历史占位屏，保持类名即可）。
启动器 `MainActivity` 复用曲库 Activity；曲库、设置、搜索、编辑器、批量、艺术家/专辑浏览都已是 Miuix。

## 曲库界面（已实现）

对齐参考版式的元素：

* 顶部：左侧齿轮（设置）、居中「歌曲（N）」、右侧搜索 / 排序 / 刷新 / 批量编辑。
* 行：48dp 圆角（6dp）封面，底部压半透明黑条 + 8sp 格式角标（FLAC/MP3/M4A…）；
  标题 15sp 粗体，`艺术家 - 专辑` 13sp；右列时长 12sp 与 `NNN kbps` 11sp。
* 分组：A–Z 与 `#` 分组头；`#` 排在 Z 之后（`PinyinIndex.rankOf`），不是按字符比较。
* 索引条：右缘字母列，拖动连续滚动、点一下滚到该分组；选中分组的气泡显示在字母列左侧，
  字母列本身始终贴着屏幕右缘。
* 排序：标题 / 艺术家 / 专辑 / 时长，可升降序；改动只在 `remember(tracks, sort, ascending)`
  里重算，避免滚动时重复做拼音转换。
* 底部：歌曲 / 艺术家 / 专辑三 Tab，三者是曲库的独立视图：歌曲 Tab 恒为全库（只受搜索框
  影响），艺术家与专辑来自内存分组（`MediaGrouping`）。点开一行会在曲库之上压入**独立的
  详情页**（自己的返回箭头 + 系统返回），只列该艺术家/专辑的作品，歌曲 Tab 不被改动。
* 搜索：顶部搜索图标展开 MIUI 风格胶囊输入框，实时过滤（沿用 `MediaFilters.matchesQuery`）；
  查询只作用于歌曲 Tab，不会缩小艺术家/专辑列表。

## 已迁移到 Miuix 的屏幕（M11 完成）

* 曲库：见上一节。
* 设置：`TopAppBar`（返回 / 重置）+ 分组行；布尔项是 Miuix `Switch`，数字与文本项是行内输入；
  底部显示键数量与存储位置。
* 搜索：三个查询行 + 「搜索」「自动补全全部来源」，结果为 `BasicComponent` 行并在右侧显示来源，
  点开先预览字段再决定是否应用。
* 编辑器：文件信息、十个行内字段、歌词多行编辑、带 48dp 预览的封面行（更换 / 移除）、
  MP3 的 ID3v1 / ID3v2.3 开关、底部整宽保存行。
* 批量：返回箭头、「N 首 / M 个字段」摘要、只填充缺失项开关、每个字段一个输入行、写入标签行。
* 艺术家 / 专辑：曲库内的两个 Tab（`ArtistList` / `AlbumList`），分组来自 `MediaGrouping`
  （拼音序），点开一行压入 `GroupDetailScreen` 详情页；`GroupBrowserActivity`（历史
  `ArtistActivity` / `AlbumActivity`）仍作为兼容空壳保留，不参与这条路径。

## 已知未验证项

* **没有真机/模拟器验证**：项目按约定跳过设备测试，因此以下只能由静态检查与单测保证：
  封面实际解码、滚动性能、索引条点击滚动、深色模式对比度、刘海/手势条内边距、输入法行为。
* 行内 `⋮` 菜单（重命名/删除/分享/写入艺术家封面）尚未接入，属于后续里程碑。
* 自定义 Tab 图标（音符 / 人群 / 唱片）未绘制，暂用 Miuix 内置图标代替。
* 深色主题走 `darkColorScheme()`，未接入 Monet 取色；`primary_color`/`accent_color` 设置项目前
  还不影响 Miuix 主题。
* 艺术家 / 专辑的详情页按专辑 + 音轨号排序，`track` 标签为空的文件会退化成按标题拼音序；
  尚未提供「返回时保持滚动位置」以外的过渡动画。

## 文案

* 默认资源即简体中文（`values/strings.xml`），英文在 `values-en/strings.xml`。
* `StringResourceParityTest` 强制：两套 key 完全一致、默认值非空且含中文（`library_bitrate`
  这类纯单位串在 `ASCII_ALLOWED` 里说明原因）、同一 key 的格式参数一致。
* 尚未迁移的屏幕仍有硬编码英文文案（编辑器、搜索、设置、批量），在对应里程碑中替换；
  CI 的「禁止硬编码文案」断言会在收尾时启用。

## 性能与启动

* 启动器 `MainActivity` 直接复用曲库 Activity，不再先创建一个空 Activity 再跳转，保留原组件名。
* 曲库搜索在输入时立即更新文本，短暂合并连续输入后在后台线程过滤；过滤不再为每首曲目拼接临时
  字符串，排序也在后台线程完成，避免大曲库时阻塞主线程。
* 封面旧版系统回退解码按缩略图尺寸采样；封面 LRU 按估算像素内存限额，而不是固定缓存条数。
* Tab 与艺术家/专辑详情页使用短淡入/轻微横移过渡；封面加载后淡入，避免突然闪现。首轮加载时媒体
  查询与设置读取并行执行，避免串行等待。
* 仍需真机验证大曲库滑动、封面加载与启动时延；本项目没有性能基准或设备自动化测试。
