# Compatibility report — rewrite vs original 1.2.5.2

Generated from real build artifacts, not from the source manifests.

| Item | Value |
|---|---|
| Baseline | `com.xjcheng.musictageditor` 1.2.5.2 (versionCode 59), SHA-256 `160dac71…bce8` |
| Candidate | CI run #16 artifact `app-debug.apk` (commit `59951a6`) |
| Tools | `tools/manifest_contract.py`, `tools/compat_diff.py` |
| Verdict | **compatible: no caller-visible component, filter or authority was lost** |

## 1. Surface diff

```
$ python3 tools/compat_diff.py --old 音乐标签_1.2.5.2.apk --new app-debug.apk

package: com.xjcheng.musictageditor (identical)
activities: 15 shared, 0 missing, 2 new
services: 2 shared, 0 missing, 0 new
providers: 2 shared, 0 missing, 1 new
note: activities: new component: androidx.activity.ComponentActivity
note: activities: new component: androidx.compose.ui.tooling.PreviewActivity
note: providers: new component: androidx.startup.InitializationProvider

compatible: no caller-visible component, filter or authority was lost
```

The three "new" entries are injected by libraries and are additive only:

| Component | Injected by | Effect on callers |
|---|---|---|
| `androidx.activity.ComponentActivity` | activity-compose (debug tooling) | none |
| `androidx.compose.ui.tooling.PreviewActivity` | compose ui-tooling (debug only) | none |
| `androidx.startup.InitializationProvider` | androidx.startup | content provider at app startup |

## 2. Merged-manifest contract check

```
$ python3 tools/manifest_contract.py --verify-apk app-debug.apk \
      --expect docs/MANIFEST_CONTRACT.json
note: activities: extra component present (allowed): androidx.activity.ComponentActivity
note: activities: extra component present (allowed): androidx.compose.ui.tooling.PreviewActivity
note: providers: extra component present (allowed): androidx.startup.InitializationProvider
manifest contract OK
```

The shipped artifact reports:

```
package: name='com.xjcheng.musictageditor' versionCode='1000000' versionName='1.0.0'
targetSdkVersion:'35'
```

## 3. What was compared

* package / applicationId
* every `<activity>`, `<service>`, `<provider>` name (15 / 2 / 2) and its **effective**
  `exported` value (pre-API-31 implicit default accounted for)
* every `intent-filter`: actions, categories, schemes, MIME types (20), hosts (1),
  `pathPattern`s (24), filter `meta-data`
* provider `authorities` and `grantUriPermissions`
* `res/xml/searchable.xml`: authority, selection (`" ?"`), suggest intent action and data
* `res/xml/provider_paths.xml`: the `storage` root

## 4. What was deliberately not compared

UI, layouts, resources, `versionName`/`versionCode`, SharedPreferences keys, the
`tagshistory` schema and the Compose implementation. Callers cannot observe these; the
rewrite is free to differ.

## 5. Residual risks

| Risk | Status |
|---|---|
| A caller checks the signing certificate before launching | unavoidable: the original private key is gone. Documented in `docs/COMPAT.md` §7 |
| A caller relies on `content://` write-back | the original converted URIs to paths and often failed; the rewrite resolves via `ContentResolver` and probes readability/writability (`SongDetailActivity`) |
| Behaviour *inside* the editor (tag writing, providers) | out of scope for this report; tracked feature-by-feature in `docs/PARITY.md` |
