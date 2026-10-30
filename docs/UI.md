# 界面实现与验证状态

## 设计语言

界面使用 [Miuix](https://github.com/compose-miuix-ui/miuix) 0.7.2（Apache-2.0）——MIUI/HyperOS
的 Compose 设计语言。主题入口在 `ui/theme/Theme.kt`：

```kotlin
MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
    MaterialTheme(colorScheme = materialColors, content = content)   // 迁移期过渡
}
```

Material 3 暂时保留在内层，尚未迁移的屏幕（编辑器、搜索、设置、批量）仍用它；这些屏幕在
后续里程碑会换成 Miuix 组件，届时移除 material3 依赖。

## 曲库界面（已实现）

对齐参考版式的元素：

* 顶部：左侧齿轮（设置）、居中「歌曲（N）」、右侧搜索 / 排序 / 刷新 / 批量编辑。
* 行：48dp 圆角（6dp）封面，底部压半透明黑条 + 8sp 格式角标（FLAC/MP3/M4A…）；
  标题 15sp 粗体，`艺术家 - 专辑` 13sp；右列时长 12sp 与 `NNN kbps` 11sp。
* 分组：A–Z 与 `#` 分组头；`#` 排在 Z 之后（`PinyinIndex.rankOf`），不是按字符比较。
* 索引条：右缘字母列，点一下滚到该分组。
* 排序：标题 / 艺术家 / 专辑 / 时长，可升降序；改动只在 `remember(tracks, sort, ascending)`
  里重算，避免滚动时重复做拼音转换。
* 底部：歌曲 / 艺术家 / 专辑三 Tab；艺术家与专辑来自内存分组（`MediaGrouping`），点一行会
  切回歌曲 Tab 并按该名字筛选。
* 搜索：顶部搜索图标展开 MIUI 风格胶囊输入框，实时过滤（沿用 `MediaFilters.matchesQuery`）。

## 已知未验证项

* **没有真机/模拟器验证**：项目按约定跳过设备测试，因此以下只能由静态检查与单测保证：
  封面实际解码、滚动性能、索引条点击滚动、深色模式对比度、刘海/手势条内边距。
* 艺术家与专辑 Tab 目前是「分组 + 筛选」而非独立页面；`activity.ArtistActivity` /
  `activity.AlbumActivity` 仍是占位实现（兼容契约要求保留类名），独立页面在后续里程碑补上。
* 行内 `⋮` 菜单（重命名/删除/分享/写入艺术家封面）尚未接入，属于后续里程碑。
* 自定义 Tab 图标（音符 / 人群 / 唱片）未绘制，暂用 Miuix 内置图标代替。
* 深色主题走 `darkColorScheme()`，未接入 Monet 取色；`primary_color`/`accent_color` 设置项目前
  还不影响 Miuix 主题。

## 文案

* 默认资源即简体中文（`values/strings.xml`），英文在 `values-en/strings.xml`。
* `StringResourceParityTest` 强制：两套 key 完全一致、默认值非空且含中文（`library_bitrate`
  这类纯单位串在 `ASCII_ALLOWED` 里说明原因）、同一 key 的格式参数一致。
* 尚未迁移的屏幕仍有硬编码英文文案（编辑器、搜索、设置、批量），在对应里程碑中替换；
  CI 的「禁止硬编码文案」断言会在收尾时启用。
