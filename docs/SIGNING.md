# Signing

The release key is **not** in this repository. It lives in a separate private repository:

> **`JunRutey/musictag-signing`** (private) — keystore, password and the scripts that push
> the Actions secrets. Losing it means no further update can be installed over a released
> build.

## Key facts

| | |
|---|---|
| Type | PKCS#12, RSA 4096-bit, SHA384withRSA |
| Alias | `musictag` |
| Valid until | 2054-03-11 |
| Certificate SHA-256 | `51:48:5C:4E:5E:A8:51:00:5C:D7:20:51:DF:BF:0A:C8:CE:64:F6:B2:11:59:9B:55:6E:70:4C:6A:D7:D7:9D:4D` |

## How a release is produced

1. CI builds `app-release-unsigned.apk`
   (`./gradlew assembleRelease -Pmusictag.unsignedRelease=true`) and uploads it as the
   `app-release-unsigned` artifact. The keystore never enters CI logs or artifacts.
2. The artifact is signed with `apksigner` (v1 + v2 + v3):

   ```sh
   apksigner sign      --ks musictag-release.p12 --ks-pass pass:"$STORE_PASSWORD" --ks-key-alias musictag      --key-pass pass:"$STORE_PASSWORD"      --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true      --out musictag-android-<version>.apk app-release-unsigned.apk
   ```

   `verify-signing.sh` in the key repository checks the result against the fingerprint.
3. The signed APK is attached to the GitHub release.

`v1.0.0` was produced this way (2.9 MB unsigned, 3.1 MB signed).

## Automated signing (optional)

`.github/workflows/release.yml` can sign automatically when four repository secrets exist:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | base64 of the keystore |
| `SIGNING_STORE_PASSWORD` | store password |
| `SIGNING_KEY_ALIAS` | `musictag` |
| `SIGNING_KEY_PASSWORD` | store password (PKCS#12 uses one) |

`set-gh-secrets.sh` in the key repository sets all four with the GitHub CLI. They were not
configured for `v1.0.0`; that build was signed from the downloaded artifact instead.

## Verifying a download

```sh
apksigner verify --print-certs musictag-android-1.0.0.apk
# Signer #1 certificate SHA-256 digest: 51485c4e5ea851005cd72051dfbf0ac8ce64f6b211599b556e704c6ad7d79d4d
```

## Why the signature differs from 1.2.5.2

The original app was signed with the author's key, which is unavailable. Android refuses to
install an update signed by a different key, so uninstalling the old app is required once.
Tags live inside the audio files and survive; old bookmarks, history and preferences do not
(see `docs/COMPAT.md`).
