# AGENTS.md — preisler architecture & working agreement

> Status: **Approved (Goal 2, 2026-11-14).** Goal 3 (skeleton + CI) done; next up is Goal 4a.
> This file is the single source of truth for the architecture. Every goal that changes the
> architecture must update it in the same commit.

## 1. Requirements (initial, complete)

| ID | Requirement |
|----|-------------|
| R1 | App launches into a polished home screen with a button; the camera opens only after pressing it. |
| R2 | Scanning takes one picture **for processing only** — no image is ever written to storage. |
| R3 | Processing extracts receipt details; the primary, hardest target is the **total price** (合計). Approach: full OCR + custom rules. |
| R4 | (Future) Send extracted data over the local network to a desktop application (e.g. a Python script). The architecture must make this easy to add later. |
| R5 | Full release cycle on GitHub Actions CI; builds are downloadable/installable artifacts. |

## 2. High-level design

```
┌─────────────────────────── :app (Android) ───────────────────────────┐
│                                                                       │
│  HomeScreen ──tap──▶ ScanScreen ──capture──▶ ResultScreen             │
│  (Compose)           (CameraX Preview +       (total, confidence,     │
│                       ImageCapture, in-mem)    details, retry)        │
│                            │                         ▲                │
│                            ▼                         │                │
│                     ImageProxy (RAM only)            │                │
│                            │                         │                │
│                            ▼                         │                │
│                  OcrEngine (interface)               │                │
│                  └ MlKitJapaneseOcr  ──▶ OcrDocument ┘                │
│                                              │                       │
│                  ResultExporter (interface) ◀┘  (future: LAN)        │
└──────────────────────────────────────────────┼───────────────────────┘
                                               ▼
┌──────────────── :receipt-core (pure Kotlin/JVM, no Android) ──────────┐
│  OcrDocument ─▶ RowAssembler ─▶ Normalizer ─▶ CandidateExtractor      │
│                                         ─▶ Scorer + ConsistencyChecks │
│                                         ─▶ ReceiptResult (JSON-able)  │
└───────────────────────────────────────────────────────────────────────┘
```

### Modules

| Module | Type | Responsibility |
|--------|------|----------------|
| `:app` | Android application | UI, navigation, camera, the ML Kit adapter, and (later) the LAN exporter. Holds no parsing logic. |
| `:receipt-core` | Pure Kotlin/JVM library | Every piece of extraction logic. It has no Android dependencies, so it tests quickly on the JVM in CI, and a future desktop tool or CLI could reuse it. |

There are just two modules on purpose. More get added only when a real need shows up, and this file must record that change.

### Key technology choices

| Concern | Choice | Why |
|---------|--------|-----|
| Language / UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose | Modern and declarative, and makes a good-looking home screen cheap to build (R1). |
| State | MVVM: `ViewModel` + `StateFlow`, one-way data flow | Simple and testable. |
| DI | Manual (a small `AppContainer`) | The app is small, so Hilt would be overhead. Revisit if it grows. |
| Camera | **CameraX** `Preview` + `ImageCapture.takePicture(executor, OnImageCapturedCallback)` | Delivers an in-memory `ImageProxy` and never writes a file (R2). *Rejected:* the ML Kit Document Scanner, which hands results back as file URIs and so breaks R2. |
| OCR | **ML Kit Text Recognition v2 – Japanese, bundled model** (`com.google.mlkit:text-recognition-japanese`) | On-device, works offline, needs no Play Services download, handles kanji/kana/digits, and returns lines with bounding boxes. |
| Serialization | kotlinx.serialization (JSON) | Used for test fixtures, the result model, and the future LAN payload. |
| Build | Gradle 9.3.1 (Kotlin DSL), AGP 9.1.1 with built-in Kotlin, Kotlin 2.3.20, Compose BOM 2026.04.01; all versions in `gradle/libs.versions.toml` | Reproducible CI. compileSdk/targetSdk 36 until we move to Compose 1.12 (needs 37). |
| Quality gates | Unit tests, Android Lint, ktlint (via Spotless) | Fail CI on any violation. |
| minSdk / targetSdk | 26 / 36 | Covers essentially every phone in use in Japan. |

## 3. Privacy guarantees (R2)

- The image only ever lives as an `ImageProxy` / `InputImage` in RAM. It is closed right after OCR and never written to disk or cache.
- The app requests no storage permission. **No `INTERNET` permission** until the LAN goal; even then only local-network traffic is allowed.
- The only permission is `CAMERA`, requested when the user taps scan, with a clear explanation if they deny it.
- Gradle task `:app:verify<Variant>PrivacyManifest` (wired into `check` and CI) fails the build if the merged manifest requests INTERNET or any storage/media permission. It was proven by a deliberate negative test on CI. See SKILLS.md → Privacy guard.
- Backups and device transfer are disabled (`allowBackup=false`, data extraction rules exclude everything).

## 4. Extraction pipeline (`:receipt-core`) — the core problem (R3)

The input is an `OcrDocument`: a list of `OcrLine(text, boundingBox)`, our own model. ML Kit types are mapped to it in `:app`. The pipeline is deterministic and works the same on the JVM and on Android.

1. **RowAssembler**: ML Kit often splits one printed row into separate blocks, with the label on the left (`合計`) and the price on the right (`¥1,280`). Lines whose vertical extents overlap are merged into visual rows and sorted left→right and top→bottom. The rule is *fix the layout before the text*.
2. **Normalizer**:
   - NFKC normalization, so full-width digits and symbols become ASCII and `￥`/`\` become `¥`.
   - Fixes common OCR confusions inside numeric tokens: `O→0`, `l/I→1`, `S→5`, `B→8`, `,`↔`.`.
   - Removes stray spaces inside numbers and inside keywords (`合 計` → `合計`).
3. **CandidateExtractor**: finds every money token (`¥1,280`, `1,280円`, `\1280`, `1280` next to a keyword) together with its row and position.
4. **Scorer**: each candidate gets a score from rule features:
   - **Positive keywords:** `合計`, `お会計`, `総合計`, `お買上合計`, `ご請求額`, `領収金額`, `お支払金額`
   - **Negative keywords:** `小計`, `お預り/お預かり`, `お釣り/釣銭`, `内税`, `消費税`, `外税`, `対象`, `ポイント`, `値引/割引`, `点数`
   - **Layout signals:** position on the page (lower is more likely), larger glyph height (totals are often printed double-size), and the `¥` prefix.
5. **ConsistencyChecks** (cross-validation):
   - `小計 + 外税 = 合計`
   - `お預り − お釣り = 合計`
   - `8%対象 + 10%対象 = 合計`
   - The tax implied by `内税` is consistent with the total.

   A candidate that satisfies a check gets a large boost. When checks disagree, confidence goes down.
6. **Output**: a `ReceiptResult`:

   ```kotlin
   @Serializable data class ReceiptResult(
       val schemaVersion: Int = 1,
       val total: Long?,            // yen, integer
       val confidence: Confidence,  // HIGH / MEDIUM / LOW / NONE
       val subtotal: Long? = null,
       val taxLines: List<TaxLine> = emptyList(), // rate, base, tax
       val date: String? = null,    // ISO-8601, later goal
       val storeName: String? = null, // later goal
       val rawText: String,         // normalized OCR text, for debugging/export
   )
   ```

The UI shows the total in large type with its confidence, shows the supporting evidence (for example "小計 1,185 + 税 95 = 1,280 ✓"), and offers **Retry** at all times. Editing the result by hand is out of scope for now.

## 5. Testing strategy & test targets

The core problem is solved in `:receipt-core` and tested on the JVM against a growing corpus of real receipts.

```
receipt-core/src/test/resources/receipts/
  0001-lawson/
    ocr.json        # serialized OcrDocument captured from a real device run
    expected.json   # reference output: { "total": 1280, "subtotal": 1185, ... }
    source.md       # where it came from (user photo / web), store type, notes
```

- **How fixtures are made:** a **debug-only** "Export OCR" button on the result screen shares the `OcrDocument` JSON (text only, never the picture) through the Android share sheet. The user scans a receipt, sends the JSON along with the true total, and the agent adds a fixture. Receipts found on the web can be added the same way after running them through OCR on a device or emulator.
- **Test targets:**
  - Each fixture is one parameterized test. `total` must match exactly. Other fields are checked only when `expected.json` lists them.
  - There are also focused unit tests for each pipeline stage, such as normalizer tables and row assembly geometry.
  - Merge gate: **100% of the corpus passes.** A new receipt that fails gets committed together with the fix, never on its own (project rule: one commit only when all tests pass).
  - The README shows corpus size as a metric.
- **Optional, later:** an instrumented test on a CI emulator that runs ML Kit on a few anonymized receipt images, to catch OCR regressions when the ML Kit version changes.
- **UI:** Compose UI tests for the navigation Home → Scan → Result, using a fake `OcrEngine` and a fake camera source.

## 6. CI / release (R5) — GitHub Actions

| Workflow | Trigger | Steps | Output |
|----------|---------|-------|--------|
| `ci.yml` | push to any branch, PR to `main` | JDK 17 → Gradle cache → `spotlessCheck` → `:receipt-core:test` → `:app:testDebugUnitTest` + `lintDebug` + privacy guard → print permissions → `assembleDebug` | `preisler-debug-<sha>` artifact (installable, debug-signed, app ID `dev.strlst.preisler.debug`) |
| `release.yml` | tag `v*` | secret check → same checks (release variant) → `assembleRelease` (R8 minified), signed with keystore from secrets → `gh release create` | Signed `preisler-<tag>.apk` attached to the release |

- Version name comes from the tag. Version code comes from the CI run number.
- `main` stays green. The agent workspace cannot build Android, so CI is the build machine: WIP goes on a `goal-<n>-<topic>` branch until CI is green, then it is squashed into one commit on `main` (SKILLS.md → Verify a change).

## 7. Future: LAN export to desktop (R4) — designed now, built later

- `ResultExporter` interface in `:app`: `suspend fun export(result: ReceiptResult): ExportOutcome`. The initial implementation does nothing.
- Planned implementation, `LanExporter`:
  - Discovery via **Android NSD (mDNS/DNS-SD)** service type `_preisler._tcp`.
  - The desktop Python script advertises itself with `zeroconf` and runs a tiny HTTP server.
  - The phone sends `POST /receipts` with the `ReceiptResult` JSON.
  - A one-time pairing code shown on the desktop prevents unwanted senders.
- `ReceiptResult` carries `schemaVersion`, so the payload contract is stable from day one.
- Adding it means adding the INTERNET permission, which gets documented here and in the README.

## 8. Goal roadmap (from the project brief)

1. ✅ Repo + README + MIT license
2. ✅ This architecture plan (approved)
3. ✅ Skeleton: both modules, Compose home screen, nav to a placeholder scan screen, CI building the debug APK artifact, release workflow
4. Core loop, in order:
   - 4a. Camera capture in memory → ML Kit OCR → raw text on the result screen, plus the debug "Export OCR" button
   - 4b. `:receipt-core` pipeline v1 (row assembly, normalization, keyword scoring) plus the first fixtures
   - 4c. Consistency checks and confidence; result screen UI with evidence
   - 4d. Grow the corpus with harder receipts (thermal fade, skew, 内税 vs 外税, multiple tax rates)
   - 4e. Secondary fields: date and store name
   - 4f. LAN export plus the Python desktop receiver
5. After each 4x step: refactor/simplify pass, update this file and SKILLS.md

## 9. Agent working rules

- Work on one goal at a time. Don't start the next goal without the user's OK when the goal is a planning gate.
- Commit to `main` only when every test, lint, format and privacy check passes on CI (see SKILLS.md → Verify a change).
- Keep `AGENTS.md` (architecture) and `SKILLS.md` (repeatable procedures, such as "add a receipt fixture", "cut a release", "bump ML Kit") current in the same commit as the change.
- Ask when unsure. Never invent expected outputs for fixtures: reference totals come from the user or from the receipt image itself.
- Commit trailer format: see the session attribution lines.
