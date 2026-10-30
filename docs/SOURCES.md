# 在线搜索源

本文件说明 1.1.0 的搜索源集合、各自的端点、优先级键，以及 Apple Music 令牌的获取方式。
`docs/SETTINGS.md` 里 `tags_sources_*` 的取值就是下表的「键」。

## 保留的源

| 键 | 名称 | 能力 | 端点 | 备注 |
|---|---|---|---|---|
| `netease` | 网易云音乐 | 标签 / 歌词 / 封面 | `music.163.com` weapi（AES + RSA） | 需要 `csrf` 与 `weapi` 加密，见 `crypto/Weapi.kt` |
| `qq` | QQ 音乐 | 标签 / 歌词 / 封面 | `u.y.qq.com/cgi-bin/musicu.fcg`、`c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg` | 歌词是 JSONP 包裹的 base64 + XML 转义文本 |
| `kuwo` | 酷我音乐 | 标签 / 封面 | `www.kuwo.cn` 搜索接口 | 返回字段较少，主要价值是中文曲库覆盖 |
| `kugou` | 酷狗音乐 | 标签 / 歌词 | `mobilecdn.kugou.com` → `krcs.kugou.com` → `lyrics.kugou.com` | 两步取歌词：先候选（hash + accesskey），再下载 |
| `apple` | Apple Music | 标签 / 封面 | `amp-api.music.apple.com/v1/catalog/{storefront}/…` | 需要网页播放器的短期开发者令牌，见下 |
| `itunes` | iTunes Search | 标签 / 封面 | `itunes.apple.com/search`、`/lookup` | 免令牌，英文专辑信息较全 |
| `musicbrainz` | MusicBrainz | 标签 | `musicbrainz.org/ws/2` | 需要带 `User-Agent`，有速率限制 |
| `coverartarchive` | Cover Art Archive | 封面 | `coverartarchive.org/release/{id}/front-*` | 配合 MusicBrainz 的 release id |

默认顺序：

* 标签 `netease,qq,kuwo,kugou,apple,itunes,musicbrainz`
* 封面 `apple,netease,qq,itunes,coverartarchive,kuwo,musicbrainz,kugou`
* 歌词 `netease,qq,kugou,kuwo`

顺序由 `MergePolicy.fromSettings` 解析：逗号、分号、空格、换行都可以作分隔符，重复项去重，
无法识别的键（含已下线的 `vgmdb`、`lastfm`）被忽略而不是抛错，所以旧配置不会导致崩溃。

## 已移除

| 键 | 原因 |
|---|---|
| `vgmdb` | 只覆盖游戏/动画原声，HTML 抓取脆弱，与四中文源定位不符 |
| `lastfm` | 需要用户自带 API key，默认无 key 时完全不工作 |

两者的 Provider、API 客户端、fixture、单测与 `lastfm_api_key` 设置项都已删除。旧配置里的键
会被静默忽略。

## Apple Music 令牌

1. `GET https://music.apple.com`，取回 HTML。
2. 从 HTML 中匹配 `/assets/index[~-]*.js`，取回该脚本。
3. 脚本里有多枚 JWT；逐枚 base64url 解析 payload，要求 `exp` 大于「当前时间 + 60 秒」，
   且 `root_https_origin`（缺失时放行）包含 `music.apple.com`。
4. 缓存该令牌直到 `exp - 60s`；搜索/详情遇到 `401` 时强制重新抓取一次再重试。
5. 请求头带 `Authorization: Bearer <token>`、`Origin: https://music.apple.com` 与桌面版 UA。

实现只依据网页播放器公开可观察的行为，并从公开 Web 接口的响应形状建立 fixture
（`core/net/src/test/resources/providers/apple/`）。**没有**拷贝任何第三方插件的代码：
`Replica0110/Lyrico-Plugins` 仓库未声明许可证，因此只参考了它的公开协议面。

区域与语言：`apple_region` 决定 `storefront`（默认 `cn`），`apple_language` 留空时跟随区域
（`cn`/`sg` → `zh-Hans`，`hk`/`tw` → `zh-Hant`，`jp` → `ja`，`kr` → `ko`，其余 `en`）。
封面把 `artwork.url` 模板里的 `{w}`/`{h}`/`{f}` 换成 `apple_cover_size` 指定的边长（默认 3000）。

## 失败语义

每个源的异常都会被 `ProviderRegistry` 记成 `ProviderFailure` 并跳过，其余源继续。搜索界面
把失败源列出来，不会因为某一个源不可用而中断整次补全。
