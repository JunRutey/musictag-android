# Testing

The project has no developer machine in the loop: CI compiles, tests and packages. That
makes the test suite the only place where behaviour is pinned down, so everything that can
run without a device runs on the JVM, and the device-only checks are scripted.

## Layers

| Layer | Where | Runs on | What it protects |
|---|---|---|---|
| Static self-check | `tools/selfcheck.py` | any machine | malformed XML, missing classes/resources, broken workflows |
| Source contract test | `app/src/test/.../ManifestContractTest.kt` | JVM | component names, exported flags, filters, MIME/path lists, authorities, searchable values |
| Unit tests | `app/src/test/**` | JVM | tag round-trips, LRC transforms, crypto vectors, request builders |
| Merged-manifest contract | `tools/manifest_contract.py --verify-apk` | CI, after build | libraries adding/removing components or flipping `exported` |
| Instrumented tests | `app/src/androidTest/**` | CI emulator (API 30/34) | intent handling, `content://` read/write-back, services, batch cancellation |
| Manual device checklist | this document | a phone | anything the emulator cannot fake (real SD cards, OEM quirks) |

## Commands

```bash
python3 tools/selfcheck.py
python3 tools/manifest_contract.py --from-xml app/src/main/AndroidManifest.xml \
    --package com.xjcheng.musictageditor --out docs/MANIFEST_CONTRACT.json
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 tools/manifest_contract.py --verify-apk app/build/outputs/apk/debug/app-debug.apk \
    --expect docs/MANIFEST_CONTRACT.json
python3 tools/compat_diff.py --old original-1.2.5.2.apk --new app-debug.apk
```

## Tag engine: two builds of the same sources

`native/taglib` is compiled twice:

* **Android** — AGP's `externalNativeBuild` produces the packaged `.so` for
  `arm64-v8a`, `armeabi-v7a` and `x86_64`.
* **Host (Linux)** — the `native` CI job runs

  ```bash
  cmake -S native/taglib -B build/host -DCMAKE_BUILD_TYPE=Release
  cmake --build build/host --parallel
  MUSICTAG_HOST_LIB="$PWD/build/host/libmusictag_jni.so" ./gradlew :core:tag:test
  ```

  `:core:tag` loads the library from `MUSICTAG_HOST_LIB` when set, otherwise through
  `System.loadLibrary("musictag_jni")` on device. Without the variable the native tests
  are skipped through `org.junit.Assume`, so a laptop without cmake stays green.

Fixtures are generated, not committed by hand:
`python3 tools/gen_fixtures.py` writes `core/tag/src/test/resources/fixtures/`, and
`tools/selfcheck.py` fails when a committed fixture drifts from the generator.

## Fixtures

Tag-engine tests use small, generated, license-free container files (built by
`tools/gen_fixtures.py` from M1) plus two real-world samples kept under 1 MB. Generated
fixtures make byte-level assertions possible; the real samples catch "valid but unusual"
files that synthetic fixtures always miss.

## Emulator job

`.github/workflows/emulator.yml` boots an x86_64 Google APIs image with KVM support and
runs `connectedDebugAndroidTest`. It is manual until M2, then becomes the gate for the
document write-back matrix.

## Device checklist (run before a release)

1. **Typed VIEW** — from a file manager, open `.mp3`, `.flac`, `.m4a`, `.ape` files: the
   editor shows the file name and reports `readable: true`.
2. **Extension VIEW** — open a `.opus`, `.dsf`, `.dff`, `.wv` file from a provider that
   reports `*/*`: the app is offered and receives it.
3. **Multi-dot name** — `Artist.Album.01.flac`: still matched.
4. **SEND** — share an audio file to the app from another app: same screen, same probe.
5. **Explicit component** — from a shell/caller app,
   `am start -n com.xjcheng.musictageditor/.SongDetailActivity -d file:///sdcard/x.mp3`.
6. **SEARCH** — system search / assistant triggers the search activity; the query is shown.
7. **content:// write-back** — pick a file through a SAF provider: `writable: true` on
   Android 10+, and the save path is exercised once M2 lands.
8. **Background** — batch a directory while the screen is off (M5); notification progress
   and cancellation behave.
9. **Read-only storage** — SD card mounted read-only or file owned by another user: the app
   reports the refusal instead of writing garbage.
10. **Upgrade path** — uninstall 1.2.5.2, install the release APK, confirm tags inside files
    are intact.

## What CI cannot tell you

* Whether OEM launchers honour the adaptive icon.
* Whether a specific content provider's `rwt` mode really persists (depends on the provider).
* Visual layout on unusual aspect ratios (screenshots are attached to emulator runs once
  M2 adds UI).
