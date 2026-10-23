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
* `minSdk` 21, `targetSdk` 35. The increase over the original (`16`/`28`) is what allows
  a modern UI; it does not change the calling contract.

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

## 3. Intent filters on `SongDetailActivity`

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
* the inert `MAIN`-only intent filter the original declared on `SongDetailActivity`
  (it has no `LAUNCHER` category, so it never matched anything)

## 7. Signature and installation

The rewrite is signed with its own key, so it cannot be installed over the original app.
Uninstall the old one first. Tags live inside the audio files and survive; settings,
bookmarks and tag history are per-install and start empty.

If a caller checks the signing certificate before launching (rare, but it happens with
"trusted editor" integrations), it will reject the rewrite — no workaround exists
without the original private key.

## 8. Evidence

`tools/manifest_contract.py --verify-apk` was run against the original 1.2.5.2 APK with the
baseline generated from this repo. Result: every activity, service, provider, MIME type,
path pattern, authority and searchable declaration matched; the only reported differences
were the three `exported` flags, which the original left implicit (pre-API-31 default) while
this project must declare them explicitly for `targetSdk` 35.
