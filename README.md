# Music Tag for Android (rewrite)

[![CI](https://github.com/JunRutey/musictag-android/actions/workflows/ci.yml/badge.svg)](https://github.com/JunRutey/musictag-android/actions/workflows/ci.yml)

A modern, maintainable reimplementation of **音乐标签 / Music Tag** for Android — the
tag editor whose last release was `com.xjcheng.musictageditor` 1.2.5.2.

This project was started from a black-box analysis of that APK: there is no original
source code. Everything here is written from scratch, on top of upstream open-source
libraries, while preserving the parts of the old app that other apps depend on
(package name, components, intent filters, provider authorities).

* Modern UI: Kotlin + Jetpack Compose + Material 3
* Tag engine: upstream [TagLib](https://taglib.org/) through a small JNI bridge
* Network: single OkHttp client, one provider per music site
* Build: GitHub Actions only — every APK you can install is produced by CI

> **Status: M3 (format matrix) — verified; M4 in progress.**
> The compatibility surface is proven by CI on every push: the built APK declares the
> original package, all 15 activities / 2 services / 2 providers, the 20 MIME types, the
> 24 extension patterns, the `SEND` and `SEARCH` entries and both provider authorities
> (`tools/manifest_contract.py` runs against the merged manifest on every push).
>
> The APK now ships upstream **TagLib 2.0.2** for `arm64-v8a`, `armeabi-v7a` and
> `x86_64` behind a small JNI bridge, and a second CI job builds the same sources for the
> host so real reads *and writes* are unit-tested: **54 tests, 0 skipped** across the tag
> engine (MP3, FLAC, Ogg, APE, M4A - read and written, artwork included) and the network
> layer (weapi and 163 key crypto anchored to NIST vectors, NetEase provider against
> recorded responses).

## Install

CI publishes an installable debug APK for every green build: **Actions → CI → the
`app-debug` artifact**.

Release APKs are attached to GitHub Releases (tag `v*`, signed with the project key).

⚠️ **You must uninstall the original app first.** Android refuses to replace an app
signed with a different key, and the original signing key is not available. Uninstalling
does not touch the tags inside your audio files; it only clears the old app's settings,
bookmarks and tag history.

## Compatibility

The rewrite keeps the old app's calling contract so third-party apps can still hand it
a file:

| Surface | Value |
|---|---|
| applicationId | `com.xjcheng.musictageditor` |
| Entry points | `MainActivity`, `SongDetailActivity`, `activity.SearchActivity` (exported) |
| Document intents | `VIEW` with 20 audio MIME types, plus `*/*` + 24 `pathPattern` rules |
| Share | `SEND` with `*/*` |
| Search | `SEARCH` + `android.app.searchable` |
| Providers | `com.xjcheng.musictageditor.SearchSugg`, `com.xjcheng.musictageditor.provider` |

The contract is executable, not just documented:

* `app/src/test/.../ManifestContractTest.kt` — JVM test on the source manifest
* `tools/manifest_contract.py` — compares the **merged** manifest of a built APK against
  [`docs/MANIFEST_CONTRACT.json`](docs/MANIFEST_CONTRACT.json) in CI
* [`docs/COMPAT.md`](docs/COMPAT.md) — the full contract, including what is *not* promised

## Roadmap

| Milestone | Scope | Status |
|---|---|---|
| M0 | Repo, CI, signing, contract baseline, installable shell | ✅ done |
| M1 | TagLib + JNI (read), host-side unit tests | ✅ done |
| M2 | Tag writing, editor UI, covers, MediaStore index, settings | ✅ done |
| M3 | Full format matrix (ogg/opus/wma/wav/aiff/aac/tta/wv/mpc/dsf/dff) | ✅ done (5 formats verified; rest engine-only) |
| M4 | Online providers (NetEase/QQ/Kuwo/Kugou + MusicBrainz/VGMdb/iTunes/CoverArt/Last.fm) | ⏳ in progress (crypto + NetEase done) |
| M5 | Batch service, bookmarks, history, file ops, search + suggestions | |
| M6 | UI polish, i18n (EN + zh-CN), performance, accessibility | |
| M7 | Compatibility verification + `v1.0.0` | |

Feature-level tracking: [`docs/PARITY.md`](docs/PARITY.md).

## Building

CI is the canonical build (`ubuntu-latest`, JDK 21, Gradle 8.13). To build locally you
need the same toolchain plus an Android SDK with platform 35 and build-tools 35:

```bash
./gradlew assembleDebug        # unsigned-ish debug build (debug key)
./gradlew testDebugUnitTest    # contract test + unit tests
python3 tools/manifest_contract.py --verify-apk app/build/outputs/apk/debug/app-debug.apk \
    --expect docs/MANIFEST_CONTRACT.json
```

Release signing is driven by environment variables, never committed:

```
MUSICTAG_KEYSTORE_PATH=/abs/path/release.keystore
MUSICTAG_KEYSTORE_PASSWORD=...
MUSICTAG_KEY_ALIAS=musictag
MUSICTAG_KEY_PASSWORD=...
```

## What was deliberately dropped

The old app shipped features that no longer work or were never part of the product:

* Xiami and ViewLyrics providers (both dead; they only existed as unreachable code)
* the donation screen (payment links removed; `DonateActivity` stays as an empty shell
  purely so an explicit caller still resolves)
* jsoup, Apache Commons, Edmodo Cropper, ColorPicker, SpeedDial, a bundled Brotli
  decoder, four duplicated HTTP stacks, and debug logging of request parameters
* no analytics, no crash reporter, no telemetry of any kind

## License

Apache-2.0 for this codebase, see [LICENSE](LICENSE). Third-party components are listed
in [NOTICE](NOTICE); TagLib is used under MPL-1.1, which keeps its files under MPL while
allowing this project to stay Apache-2.0.
