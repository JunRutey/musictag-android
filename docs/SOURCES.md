# 在线搜索源

本文件说明当前启用的搜索源、各自的端点与优先级顺序。

## 启用的源

| 键 | 名称 | 能力 | 端点 | 备注 |
|---|---|---|---|---|
| `netease` | 网易云音乐 | 标签 / 歌词 / 封面 | `music.163.com` weapi（AES + RSA） | 需要 `csrf` 与 `weapi` 加密，见 `crypto/Weapi.kt` |
| `qq` | QQ 音乐 | 标签 / 歌词 / 封面 | `u.y.qq.com/cgi-bin/musicu.fcg`、`c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg` | 歌词是 JSONP 包裹的 base64 + XML 转义文本 |
| `kuwo` | 酷我音乐 | 标签 / 封面 | `www.kuwo.cn` 搜索接口 | 返回字段较少，主要价值是中文曲库覆盖 |
| `itunes` | iTunes Search | 标签 / 封面 | `itunes.apple.com/search`、`/lookup` | 免令牌，英文专辑信息较全 |
| `musicbrainz` | MusicBrainz | 标签 | `musicbrainz.org/ws/2` | 需要带 `User-Agent`，有速率限制 |
| `coverartarchive` | Cover Art Archive | 封面 | `coverartarchive.org/release/{id}/front-*` | 配合 MusicBrainz 的 release id |

默认顺序（`MergePolicy.DEFAULT`）：

* 标签 `netease,qq,kuwo,itunes,musicbrainz`
* 封面 `netease,qq,itunes,coverartarchive,kuwo,musicbrainz`
* 歌词 `netease,qq,kuwo`

## 已移除

| 键 | 移除版本 | 原因 |
|---|---|---|
| `kugou` | 1.1.6 | 搜索接口长期返回空结果，界面上每次都显示「部分来源没有响应」 |
| `apple` | 1.1.6 | 网页播放器令牌抓取失败率高，几乎每次搜索都列入无响应来源 |
| `vgmdb` | 1.1.0 | 只覆盖游戏/动画原声，HTML 抓取脆弱，与中文源定位不符 |
| `lastfm` | 1.1.0 | 需要用户自带 API key，默认无 key 时完全不工作 |

`kugou` 与 `apple` 的 Provider、API 客户端和单测仍留在 `core/net`（它们是对公开接口的
独立实现，测试也是离线的 fixture 测试），只是不再注册进应用、也不在合并顺序里；枚举里的
键仍能被 `MergePolicy.parseOrder` 解析并忽略。`vgmdb`/`lastfm` 则是彻底删除。

## 失败语义

每个源的异常都会被 `ProviderRegistry` 记成 `ProviderFailure` 并跳过，其余源继续。搜索界面
只列出真正失败且仍在使用的源；移除上面两个源之后，正常情况下不会再出现「部分来源没有响应」。
