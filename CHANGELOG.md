# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
