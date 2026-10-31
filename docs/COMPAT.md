# Compatibility contract

Third-party apps launch `com.xjcheng.musictageditor` through four different doors. All
four are preserved, and all four are enforced by tests rather than by good intentions.

| Door | How callers use it | Where it is enforced |
|---|---|---|
| Typed document | `ACTION_VIEW` + `audio/mpeg` etc. with `file://` or `content://` | `ManifestContractTest`, `docs/MANIFEST_CONTRACT.json` |
| Extension matching | `ACTION_VIEW` + `*/*` for `.ape/.tta/.wv/.mpc/.opus/.dsf/.dff/.aac` (incl. multi-dot names) | same |
| Share sheet | `ACTION_SEND` + `*/*` | same |
| Explicit component | `ComponentName("com.xjcheng.musictageditor", "...SongDetailActivity")` | same + real classes registered |
| System search | `ACTION_SEARCH` + `android.app.searchable` | same |

## 1. Identity

* `applicationId` = `com.xjcheng.musictageditor` (never change it: it *is* the identity
  callers resolve).
* `minSdk` 24, `targetSdk` 35. The original was `16`/`28`; the floor moved to Android 7.0
  because Miuix 0.7.2 — the newest release the pinned Kotlin 2.2.0 compiler can consume —
  declares `minSdk 24`. This changes who can install the app, not the calling contract.

## 2. Components

Exported (must stay reachable):

| Component | Contract |
|---|---|
| `com.xjcheng.musictageditor.MainActivity` | `MAIN` + `LAUNCHER`, `singleTop` |
| `com.xjcheng.musictageditor.SongDetailActivity` | three filters, see §3 |
| `com.xjcheng.musictageditor.activity.SearchActivity` | `SEARCH` + `android.app.searchable` metadata |

Non-exported but registered under their historical names (explicit callers may still
target them inside the same UID, and it costs nothing to keep them stable):

`activity.SearchMediaActivity`, `activity.BatchEditSongsDetail`, `activity.SettingsActivity`,
`activity.AlbumActivity`, `activity.ArtistActivity`, `activity.SearchWebPictureActivity`,
`activity.SearchWebLyricActivity`, `activity.SearchWebTagActivity`, `activity.BatchESDSelTagPic`,
`activity.IgnoreDirSetupActivity`, `activity.DonateActivity`, `activity.CropImageActivity`,
`service.BatchService`, `service.RefreshMediaStoreService`.

`DonateActivity` is an empty shell: the donation feature is gone, the class name is kept so
an explicit caller gets a screen instead of `ActivityNotFoundException`.

Providers:

| Authority | Implementation | Notes |
|---|---|---|
| `com.xjcheng.musictageditor.SearchSugg` | `com.xjcheng.musictageditor.SearchSuggProvider` | recent search suggestions |
| `com.xjcheng.musictageditor.provider` | `androidx.core.content.FileProvider` | `grantUriPermissions=true` |

Both are written as `${applicationId}.<suffix>` in the manifest, so the **release** build
resolves to exactly the values above (what callers see, and what the contract check
asserts) while the `.debug` build gets `.debug`-suffixed authorities. That is not a
cosmetic difference: a device allows one package per provider authority, so hardcoding
them made the debug APK refuse to install next to the release APK
(`INSTALL_FAILED_CONFLICTING_PROVIDER`).

## 3. Intent filters on `SongDetailActivity`

0. **Inert `MAIN`** — no category, so it never shows in a launcher. Reproduced verbatim so
   that `resolveActivity()` / `queryIntentActivities()` answer exactly as before.
1. **Typed audio** — `VIEW` + `DEFAULT`, schemes `file`/`content`, 20 MIME types
   (`audio/ape`, `audio/x-ape`, `audio/flac`, `audio/mpeg`, `audio/x-wav`, `audio/mp4`,
   `audio/mp4a-latm`, `audio/ogg`, `audio/opus`, `audio/x-ms-wma`, `audio/x-aiff`,
   `audio/x-musepack`, `audio/x-wavpack`, `audio/x-tta`, `audio/dsf`, `audio/x-dsf`,
   `audio/dff`, `audio/x-dff`, `audio/aac`, `audio/x-aac`).
2. **Extension matching** — `VIEW` + `DEFAULT`, `*/*`, `host="*"`, 24 `pathPattern`s:
   `.*\.(ape|tta|wv|mpc|opus|dsf|dff|aac)` and the `.*\..*\.` / `.*\..*\..*\.` variants
   the original shipped for names like `Artist.Album.01.flac`.
3. **Share** — `SEND` + `DEFAULT`, `*/*`.

## 4. Search configuration

`res/xml/searchable.xml` is reproduced value-for-value:

```
searchSuggestAuthority     = com.xjcheng.musictageditor.SearchSugg
searchSuggestSelection     = " ?"
searchSuggestIntentAction  = android.intent.action.VIEW
searchSuggestIntentData    = content://com.xjcheng.musictageditor/datatable
```

## 5. Documents handed over by other apps

The original app converted a `content://` URI into a filesystem path, which works only for
providers backed by `_data` (MediaStore, and even then only on older releases). The rewrite
resolves documents through `ContentResolver` instead:

* read: `openAssetFileDescriptor(uri, "r")`
* write in place: `openAssetFileDescriptor(uri, "rw" / "rwt")`
* write through MediaStore (API 30+): `MediaStore.createWriteRequest(...)`
* persistable grants: honour `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` instead of assuming
  the URI stays valid after the activity is recreated
* if the provider forbids writing: fall back to "copy → edit → save as", and say so

`SongDetailActivity` already probes and reports readable/writable state in M0, so this
path is exercised from the first build.

## 6. Explicitly *not* promised

Changing these is allowed and does not break callers:

* UI layout, navigation, themes, icons
* SharedPreferences keys and the `tagshistory` SQLite schema
* `versionName` / `versionCode` numbering

## 7. Signature and installation

The rewrite is signed with its own key, so it cannot be installed over the original app.
Uninstall the old one first. Tags live inside the audio files and survive; settings,
bookmarks and tag history are per-install and start empty.

If a caller checks the signing certificate before launching (rare, but it happens with
"trusted editor" integrations), it will reject the rewrite — no workaround exists
without the original private key.

## 8. Evidence

Two independent tools, both runnable offline and in CI:

* `tools/manifest_contract.py --verify-apk` — compares the **merged** manifest of a built
  APK against [`MANIFEST_CONTRACT.json`](MANIFEST_CONTRACT.json). One-directional: required
  components/filters must exist, library-injected extras are reported but allowed.
* `tools/compat_diff.py --old <original>.apk --new <rewrite>.apk` — diffs the
  caller-visible surface of two APKs: package, component names, effective `exported`
  flags, intent filters (actions/categories/schemes/MIME/host/pathPattern) and provider
  authorities. UI, resources and version strings are ignored by design.

`manifest_contract.py` normalises `exported` to Android's *effective* value: when the
attribute is absent (legal for `targetSdk` < 31, which is how 1.2.5.2 was built), a
component with at least one intent filter counts as exported. That is why the three entry
points compare equal even though only the rewrite declares `android:exported="true"`.

Result against the original 1.2.5.2 APK: **package identical, 15/15 activities, 2/2
services, 2/2 providers, all 20 MIME types, all 24 path patterns, both provider
authorities and the searchable configuration match.** The only remaining difference was
the inert `MAIN` filter, which is now reproduced as well.
