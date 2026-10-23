# Feature parity tracker

Every user-visible capability of `com.xjcheng.musictageditor` 1.2.5.2, with the milestone
that delivers it and the check that proves it. "Parity" here means *the same capability*,
not the same pixels: the UI is rebuilt in Compose.

Status values: `todo` / `wip` / `done` / `dropped`.

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
| 4.1 | Read / write lyrics in tags | M2 | todo | round-trip test |
| 4.2 | Import from / export to `.lrc` | M2 | todo | round-trip test |
| 4.3 | Reformat timetags, remove timetags, adjust offset | M3 | todo | fixture test |
| 4.4 | Delete blank text lines, delete head tags | M3 | todo | fixture test |
| 4.5 | Bilingual lyrics: download translations, merge order, connectors | M4 | todo | provider fixture |
| 4.6 | Traditional ↔ Simplified conversion (tags and file names) | M3 | todo | round-trip test |
| 4.7 | `.lrc` encoding, directory and file-name modes | M2 | todo | unit test |

## 5. Online providers

| # | Provider | Endpoints | Milestone | Status |
|---|---|---|---|---|
| 5.1 | NetEase Cloud Music | `cloudsearch/pc`, `v3/song/detail`, `song/lyric`, `v1/album/{id}` + weapi crypto | M4 | todo |
| 5.2 | QQ Music | `musicu.fcg`, lyric fcgi, `T002R800x800M000` cover | M4 | todo |
| 5.3 | Kuwo | `search.kuwo.cn/r.s`, `m.kuwo.cn/songinfoandlrc` | M4 | todo |
| 5.4 | Kugou (lyrics only) | `mobilecdn` search, `m3ws` KRC | M4 | todo |
| 5.5 | MusicBrainz | release/recording search, release detail | M4 | todo |
| 5.6 | VGMdb | album search, album detail | M4 | todo |
| 5.7 | iTunes Search | `itunes.apple.com/search` (+ country parameter) | M4 | todo |
| 5.8 | Cover Art Archive | `coverartarchive.org/release/{id}/front` | M4 | todo |
| 5.9 | Last.fm | `ws.audioscrobbler.com/2.0` artist/album/track search | M4 | todo |
| 5.10 | Per-source priority for combined tags / covers / lyrics | M4 | todo | unit test |
| 5.11 | Result limit, search criteria | M4 | todo | manual |
| 5.12 | Provider health/diagnostics screen | M4 | todo | manual |

## 6. Batch operations

| # | Capability | Milestone | Status | Proof |
|---|---|---|---|---|
| 6.1 | Overwrite 11 fields across a selection | M5 | todo | instrumented test |
| 6.2 | Save each field to tag vs to `.lrc`/file | M5 | todo | instrumented test |
| 6.3 | 14 utility actions (lyric cleanup, CHT/CHS, clear tags, cover save-as, …) | M5 | todo | unit + instrumented |
| 6.4 | Rename files mode / change tags mode | M5 | todo | unit test on templates |
| 6.5 | Foreground service with progress and cancellation | M5 | todo | instrumented test |
| 6.6 | MediaStore refresh service | M5 | todo | instrumented test |

## 7. Files and search

| # | Capability | Milestone | Status |
|---|---|---|---|
| 7.1 | Rename / delete / share (FileProvider) | M5 | todo |
| 7.2 | Local search screen | M5 | todo |
| 7.3 | `SEARCH` intent + recent suggestions provider | M5 | todo |
| 7.4 | Ignored directories, minimum duration, ignore MP4 videos | M2 | todo |

## 8. Settings

47 keys carried over with their original semantics (names and defaults documented in
`docs/SETTINGS.md`, produced in M2). UI is Compose; storage is a typed repository, not raw
`SharedPreferences` reads scattered through activities.

## 9. Formats

Status legend: **read** = parsed and covered by a fixture test; **write** = editable.

| Container | Tag blocks | Read | Write |
|---|---|---|---|
| MP3 | ID3v1, ID3v2.3, ID3v2.4 | ✅ M1 (ID3v2.3 + APIC + USLT fixtures) | M2 |
| FLAC | VorbisComment + PICTURE | ✅ M1 (13 properties + cover fixture) | M2 |
| MP4/M4A | `ilst` (incl. `covr`) | engine ready, fixture M3 | M2 |
| APE | APEv2 | text via PropertyMap; cover M3 | M3 |
| Ogg Vorbis / Opus | VorbisComment | engine ready, fixture M3 | M3 |
| WMA | ASF | engine ready, fixture M3 | M3 |
| WAV / AIFF | RIFF LIST-INFO / ID3 chunk | fixture M3 | M3 |
| AAC | ADTS + leading ID3v2 | fixture M3 | M3 |
| TTA | ID3v2 / APEv2 | fixture M3 | M3 |
| WavPack | APEv2 | fixture M3 | M3 |
| Musepack | APEv2 / ID3v2 | fixture M3 | M3 |
| DSF | trailing ID3v2 | fixture M3 | M3 |
| DFF | `ID3 ` chunk (gated by setting) | fixture M3 | M3 |

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
