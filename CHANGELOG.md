# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
