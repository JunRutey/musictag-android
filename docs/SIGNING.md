# Release signing

Release builds are signed **only in CI**, from repository secrets. No keystore, password
or `key.properties` file is ever committed (see `.gitignore`).

## Required secrets

| Secret | Content |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the whole keystore file, base64-encoded (single line) |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias inside the keystore |
| `SIGNING_KEY_PASSWORD` | key password |

`.github/workflows/release.yml` decodes the keystore into `$RUNNER_TEMP/signing/release.keystore`
and exports `MUSICTAG_KEYSTORE_PATH` / `MUSICTAG_KEYSTORE_PASSWORD` / `MUSICTAG_KEY_ALIAS` /
`MUSICTAG_KEY_PASSWORD`, which `app/build.gradle.kts` picks up. If those variables are
missing, `assembleRelease` falls back to the debug key signer so normal CI keeps working.

## Create a keystore

```bash
keytool -genkeypair -v \
  -keystore release.keystore -alias musictag \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=Music Tag, O=Your Name, C=CN"
```

Then encode it for the secret (PowerShell / bash):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Set-Content keystore.b64
```

```bash
base64 -w0 release.keystore > keystore.b64
```

## Why this matters more than usual

* The APK cannot replace `com.xjcheng.musictageditor` 1.2.x anyway (different signature).
  Users uninstall the old app once; after that, **every future release must keep using the
  same keystore** or Android will refuse the upgrade and users would have to uninstall again.
* Keep a backup of the keystore and its passwords outside this repository (password
  manager / offline copy). Losing them means every user must uninstall + reinstall to get
  the next version.
* Enable `v1`+`v2`+`v3` signing (the Gradle config already does). `v3` enables key rotation
  later if the key is ever compromised.

## Local release testing

Export the four environment variables yourself and run:

```bash
./gradlew assembleRelease
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```
