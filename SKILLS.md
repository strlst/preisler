# SKILLS.md — repeatable procedures

Step-by-step recipes for recurring work. When a procedure changes, update it in the same commit. The design and its reasons live in `AGENTS.md`; this file only says *how*.

---

## Verify a change (the commit gate)

Every commit to `main` must pass the full CI pipeline. The agent workspace has no Android SDK and no access to Maven or Google repos, so **CI is the build machine**:

1. Create a branch `goal-<n>-<topic>` and push WIP commits freely.
2. `.github/workflows/ci.yml` runs on every push (contents: AGENTS.md §6). The log prints `receipt-core tests: N run, 0 failed`, the granted permissions and an APK size report.
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
3. The debug build has the application ID `dev.strlst.preisler.debug`, so it can sit next to a release build. It only runs on 64-bit ARM (real phones, or an ARM emulator).

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

For each release, either:
- **One click (recommended):** GitHub → Actions → **Release** → **Run workflow** → choose `main` and enter a version such as `v0.1.0`. The workflow checks the format, makes sure the tag doesn't exist yet, and creates it.
- **Or from a terminal:** `git tag v0.1.0 && git push origin v0.1.0`. This also works if a release was already drafted in the GitHub UI: the APK is then uploaded to it.

The agent session can push branches but **not tags**, so the user starts releases.

**Dry run (the agent can do this):** push any commit to the `release-dry-run` branch, e.g. `git push -f origin main:release-dry-run`. `release.yml` then runs every check, builds the APK signed with the real keystore and prints its certificate, but creates no tag and publishes nothing. Do this before asking the user to release whenever `release.yml`, signing or the build setup changed.

**Release candidate:** the dry run also uploads the signed APK as the artifact `preisler-release-candidate-<sha>` (kept 7 days). Code shrinking only happens in release builds, so the user installs and scans with this APK before the real release whenever code, dependencies or R8 rules changed.

**Translate an obfuscated error** from a release build (names like `og3.a`): download `mapping-<version>.txt` from the GitHub release and look up the short names (`grep -- '-> og3:' mapping-v0.1.1.txt`). For a build without a published mapping, re-run that commit through the dry run with a temporary step that prints the matching lines. R8 output is deterministic for identical inputs.

**If a release run fails** after creating its tag, fix the problem and run the workflow again with the same version. A tag without a published release is moved to the new commit automatically. AGP 9 note: unit-test tasks exist for the debug build type only (`testDebugUnitTest`), so release checks use those.

`release.yml` runs the checks, builds a minified APK signed with the release keystore, and publishes `preisler-v0.1.0.apk` as a GitHub Release. The version name comes from the tag and the version code from the run number.

## Privacy guard

What it checks: AGENTS.md §3.
- If a new dependency trips the guard, remove its permission in the manifest with `tools:node="remove"`, or pick a different library. Never weaken the list without the user's approval.
- When the LAN export lands: remove `INTERNET` from the guard list and from the `tools:node="remove"` entries, and update README → Privacy and AGENTS.md §3, all in one commit. ML Kit telemetry must then still not reach the network, for example by restricting the LAN client to local addresses.

## Check APK size

CI's **APK size report** step lists both APKs, their largest entries and the native library size per CPU type. After any dependency change, compare it with the last numbers in AGENTS.md §2 (Platforms). A new CPU type under `lib/` means `ndk.abiFilters` was bypassed. A large `classes*.dex` in release means R8 keep rules are too broad.

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
4. The export contains only text and coordinates, never the picture. It can still hold personal data. The agent deletes it before committing (see below), but you may also delete such lines yourself before sending.

## Add a receipt fixture

1. Get the OCR JSON and the true values as described above. A batch can come as `capture<N>.json` files plus a `table` (`capture;date;total`).
2. Create `receipt-core/src/test/resources/receipts/<NNNN>-<store>/` containing:
   - `ocr.json`: exactly as exported
   - `expected.json`: `{ "total": 1280, "date": "2026-11-14" }`. Add more fields only once the extractor supports them and `CorpusTest` checks them.
   - `source.md`: where it came from, store type, anything unusual (faded, skewed, 内税…)
3. Never guess an expected value. If the user hasn't given it, ask.
4. **Delete** personal data before the fixture is first committed. Remove the whole OCR line; don't use placeholders. That covers staff names and staff IDs (担当, 責, 扱, スタッフ), card details (masked number, 有効期限, 承認番号, 処理通番, AID, brand), customer and member numbers (お客様No, 会員), transaction IDs (取引No, TC#, レシートNo) and receipt barcodes. Store names, addresses, registration numbers (登録番号), dates and amounts stay. Re-run the corpus afterwards. If personal data ever reaches a commit, rewrite history (amend, or interactive rebase via `git rebase --onto`) and force-push before anything else.
5. Raise `MIN_CORPUS_SIZE` in `CorpusTest`.
6. If the new fixture fails, fix the pipeline in the same commit. The corpus must stay at 100% passing.

## Tune the extraction rules

Without a local Android/Gradle build, every CI round trip costs about 3 minutes. For rule tuning, reimplement the change in a throwaway scratch script (Python works) outside the repo and iterate against `receipt-core/src/test/resources/receipts/*/ocr.json` until 100% pass. Then port it faithfully to Kotlin and confirm on CI: the log prints `receipt-core tests: N run, 0 failed`. Never commit the scratch script, because a second implementation is technical debt. Check the score margins too: a fixture that wins by less than about 2 points is fragile, so note it in its `source.md`.
