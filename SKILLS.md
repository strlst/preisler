# SKILLS.md — repeatable procedures

These are step-by-step recipes for recurring work. Keep each one current. When a procedure changes, update it in the same commit as the change. The architecture itself lives in `AGENTS.md`.

---

## Verify a change (the commit gate)

Every commit to `main` must pass the full CI pipeline. The agent workspace has no Android SDK and no access to Maven or Google repos, so **CI is the build machine**:

1. Create a branch `goal-<n>-<topic>` and push WIP commits freely.
2. `.github/workflows/ci.yml` runs on every push and covers formatting, core tests, app tests, lint, the privacy guard, and the debug APK.
3. Poll the run and read the logs through the REST API (GraphQL is unavailable):
   ```sh
   gh api "repos/strlst/preisler/actions/runs?branch=<branch>&per_page=1" \
     --jq '.workflow_runs[0] | "\(.id) \(.status) \(.conclusion)"'
   gh api repos/strlst/preisler/actions/runs/<run>/jobs --jq '.jobs[0].id'
   gh api repos/strlst/preisler/actions/jobs/<job>/logs | grep -E "What went wrong|e: |FAILED"
   ```
   Artifact downloads are blocked from the agent workspace, so print anything you need to inspect into the job log.
4. Once the run is green, squash the branch into **one** commit on `main` with a descriptive message, push, check that the `main` run is green, then delete the branch.

With a local SDK, `./gradlew spotlessApply check :app:assembleDebug` does the same thing.

## Formatting

ktlint (`ktlint_official` style) runs through Spotless, with a 120-column limit and `@Composable` functions allowed to use PascalCase. Without a local build, keep lines ≤ 120 characters and wrap long expressions after `=`, the way ktlint does:
```sh
awk 'length > 120 {print FILENAME": "FNR}' $(git ls-files '*.kt' '*.kts')
```

## Install a debug build

1. Open GitHub → **Actions** → the latest green **CI** run on `main` → **Artifacts**, and download `preisler-debug-<sha>`.
2. Unzip it and install the APK with `adb install -r preisler-debug-<sha>.apk`, or open it on the phone and allow "install unknown apps".
3. The debug build has the application ID `dev.strlst.preisler.debug`, so it can sit next to a release build.

## Cut a release

One-time setup (done by the user, since secrets are never shared with the agent):
```sh
keytool -genkeypair -v -keystore preisler-release.jks -alias preisler \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 preisler-release.jks > keystore.b64     # macOS: base64 -i preisler-release.jks
```
Add four repository secrets under GitHub → Settings → Secrets and variables → Actions:

| Secret | Value |
|--------|-------|
| `PREISLER_KEYSTORE_BASE64` | contents of `keystore.b64` |
| `PREISLER_KEYSTORE_PASSWORD` | keystore password |
| `PREISLER_KEY_ALIAS` | `preisler` (or your alias) |
| `PREISLER_KEY_PASSWORD` | key password |

Keep `preisler-release.jks` backed up somewhere safe. Losing it means existing installs can't be updated.

For each release:
```sh
git tag v0.1.0 && git push origin v0.1.0
```
`release.yml` runs the checks, builds a minified APK signed with the release keystore, and publishes `preisler-v0.1.0.apk` as a GitHub Release. The version name comes from the tag and the version code from the run number.

## Privacy guard

`:app:verify<Variant>PrivacyManifest` fails the build if the merged manifest (including permissions pulled in by libraries) requests INTERNET, ACCESS_NETWORK_STATE or any storage or media permission. ML Kit's telemetry permissions are already stripped in `AndroidManifest.xml`. `check` depends on it, and CI prints the permissions that were granted.
- If a new dependency trips the guard, remove its permission in the manifest with `tools:node="remove"`, or pick a different library. Never weaken the list without the user's approval.
- When the LAN export goal lands, remove `INTERNET` from the list and from the `tools:node="remove"` entries, update README → Privacy, and update AGENTS.md §3, all in the same commit. Keep ML Kit telemetry from using the network some other way, for example by checking the destination host in the LAN client.

## Bump a dependency

1. Look up the latest stable version at its official release notes (AGP, Kotlin, the Compose BOM, CameraX, ML Kit).
2. Edit `gradle/libs.versions.toml` only. Build scripts never hard-code versions.
3. Mind the coupling. The Compose BOM may raise the required compileSdk and AGP (e.g. Compose 1.12 requires compileSdk 37 and AGP ≥ 9.1.1). An AGP upgrade may require a newer Gradle wrapper.
4. To regenerate the Gradle wrapper offline:
   ```sh
   cd "$(mktemp -d)" && touch settings.gradle.kts && \
     gradle wrapper --gradle-version <v> --distribution-type bin --offline
   ```
   Then copy `gradlew*` and `gradle/wrapper/*` into the repo.
5. Verify through CI as described above.

## Capture OCR from a real receipt

1. Install a **debug** build (see above). Release builds have no export button.
2. Scan the receipt. On the result screen, tap **Export OCR**.
3. Share it to wherever you can copy text from (mail, notes, chat), then paste the JSON here together with the **true total**. Add any other fields you know, such as subtotal and tax.
4. The export contains only text and coordinates, never the picture. Still, check it for anything personal (names, card numbers, member IDs) before sending. Those values may be replaced with same-length placeholders, as long as the money values stay untouched.

## Add a receipt fixture *(from Goal 4b)*

1. Get the OCR JSON and the true values as described above.
2. Create `receipt-core/src/test/resources/receipts/<NNNN>-<store>/` containing:
   - `ocr.json`: exactly as exported
   - `expected.json`: `{ "total": 1280 }` plus optional `subtotal`, `taxLines`, `date`, `storeName`
   - `source.md`: where it came from, store type, anything unusual (faded, skewed, 内税…)
3. Never guess an expected value. If the user hasn't given it, ask.
4. If the new fixture fails, fix the pipeline in the same commit. The corpus must stay at 100% passing.
