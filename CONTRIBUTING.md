# Contributing

## Ground rules

1. **Do not break the compatibility contract.** `docs/MANIFEST_CONTRACT.json` and
   `ManifestContractTest` are the guard rails: package name, component class names, exported
   flags, intent filters, provider authorities and the searchable configuration are public
   API. If a change genuinely must alter them, update the baseline in the same commit and
   explain the migration in `docs/COMPAT.md`.
2. **CI is the compiler.** Every change has to pass `CI`. Do not commit generated APKs or
   build output.
3. **No new dependency without a reason.** Prefer platform APIs and small local code; every
   dependency is a supply-chain and upgrade cost. Keep the version catalogue in
   `gradle/libs.versions.toml` as the single source of truth.
4. **No telemetry, no analytics, no ad code, no bundled payment links.**
5. **Formatting.** 4-space Kotlin, 2-space XML/JSON/YAML, LF, UTF-8, 120-column soft limit
   (see `.editorconfig`).

## Workflow

```bash
python3 tools/selfcheck.py          # fast, no SDK needed
./gradlew testDebugUnitTest
```

Open a PR with a short description of the behaviour change and, for tag-engine work, the
fixtures that prove it. Keep PRs single-purpose: one subsystem per PR.

## Where things live

| Path | Contents |
|---|---|
| `app/` | application module: manifest (contract), Compose UI, services |
| `app/src/main/java/.../compat/` | contract constants shared by code and tests |
| `app/src/test/` | JVM tests (contract, tag round-trips, crypto vectors, LRC) |
| `app/src/androidTest/` | emulator tests (intents, document write-back, services) |
| `tools/` | Python helpers: AXML decoder, contract tool, self-check, fixtures |
| `docs/` | contract, parity tracker, testing guide |

## Signs you are about to break something

* Editing `AndroidManifest.xml` component names, filters, or `exported` flags.
* Changing `applicationId`, `SearchSuggProvider.AUTHORITY`, or `provider_paths.xml`.
* Renaming anything under `com.xjcheng.musictageditor.compat`.
* Making tag writes non-atomic (write through a temp file, fsync, rename).
