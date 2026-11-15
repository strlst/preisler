# AGENTS.md — preisler architecture & working agreement

> Status: **Approved (Goal 2, 2026-11-14).** Goals 3, 4a and 4b done: total extraction passes 17/17 real receipts. Next up is the Goal 5 refactor pass, then 4c.
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
│  OcrDocument ─▶ uprightLines ─▶ assembleRows ─▶ labelOf + classify    │
│              ─▶ parseAmounts ─▶ TotalExtractor (score + cross-checks) │
│              ─▶ ReceiptResult (JSON-able)                             │
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
| DI | Manual: `AppContainer`, owned by `PreislerApplication` | The app is small, so Hilt would be overhead. Revisit if it grows. |
| Screen state | One `ScanViewModel`, scoped to the activity and shared by Scan and Result: `Idle → Processing → Done(document, result) / Failed` | The result screen reads the same in-memory state. Nothing survives process death, which is intended (R2). |
| Camera | **CameraX 1.5.1** `LifecycleCameraController` + `PreviewView`, `IMAGE_CAPTURE` use case only, `takePicture(executor, OnImageCapturedCallback)` | Delivers an in-memory `ImageProxy` and never writes a file (R2). *Rejected:* the ML Kit Document Scanner, which hands results back as file URIs and so breaks R2. |
| OCR | **ML Kit Text Recognition v2 – Japanese, bundled model** (`com.google.mlkit:text-recognition-japanese:16.0.1`), behind the `OcrEngine` fun interface; `buildOcrDocument` maps lines into upright coordinates | On-device, works offline, needs no Play Services download, handles kanji/kana/digits, and returns lines with bounding boxes. |
| Serialization | kotlinx.serialization (JSON) | Used for test fixtures, the result model, and the future LAN payload. |
| Build | Gradle 9.3.1 (Kotlin DSL), AGP 9.1.1 with built-in Kotlin, Kotlin 2.3.20, Compose BOM 2026.04.01; all versions in `gradle/libs.versions.toml` | Reproducible CI. compileSdk/targetSdk 36 until we move to Compose 1.12 (needs 37). |
| Quality gates | Unit tests, Android Lint, ktlint (via Spotless) | Fail CI on any violation. |
| minSdk / targetSdk | 26 / 36 | Covers essentially every phone in use in Japan. |

## 3. Privacy guarantees (R2)

- The image only ever lives as an `ImageProxy` / `InputImage` in RAM. `ScanViewModel` closes it with `use {}` on success and failure alike (covered by `ScanViewModelTest`). It is never written to disk or cache. Only the OCR text is kept, in memory.
- The app requests no storage permission. **No `INTERNET` permission** until the LAN goal; even then only local-network traffic is allowed.
- The only permission is `CAMERA`, requested when the user taps scan, with a clear explanation if they deny it.
- ML Kit's bundled model pulls in Google's telemetry transport, which declares `INTERNET` and `ACCESS_NETWORK_STATE`. Both are stripped with `tools:node="remove"` in our manifest, so the telemetry cannot send anything.
- Gradle task `:app:verify<Variant>PrivacyManifest` (wired into `check` and CI) fails the build if the merged manifest requests INTERNET, ACCESS_NETWORK_STATE or any storage/media permission. It was proven by a deliberate negative test on CI. See SKILLS.md → Privacy guard.
- Backups and device transfer are disabled (`allowBackup=false`, data extraction rules exclude everything).

## 4. Extraction pipeline (`:receipt-core`) — the core problem (R3)

The input is an `OcrDocument`: a list of `OcrLine(text, boundingBox)`, our own model. ML Kit types are mapped to it in `:app`. The pipeline is deterministic and works the same on the JVM and on Android. It was tuned on 17 real receipts (§5). What follows is what v1 actually does (Goal 4b).

1. **`uprightLines`** (`Layout.kt`): if most lines are more than twice as tall as they are wide, the photo was taken sideways. The boxes are rotated so the receipt reads top to bottom (text along +y, receipt top at large x).
2. **`assembleRows`** (`Layout.kt`): ML Kit often returns the label (`合計`) and the amount (`¥1,280`) as separate lines. A line joins a row when its vertical center lies inside the row's first line and that line's center lies inside it. Lines are then sorted left to right. The rule is *fix the layout before the text*.
3. **`parseAmounts`** (`Amounts.kt`): NFKC normalization, so full-width becomes ASCII and `￥`/`\` become `¥`. Two shapes count as money:
   - a ¥-prefixed number anywhere in a line, where OCR's `Y` and a leading `*` also count as ¥
   - a line that is nothing but a number plus markers (`)`, `内`, `軽`, `※`, `X`…)

   Dates, phone numbers, IDs, barcodes (more than 7 digits) and leading-zero numbers never count. Inside numbers, `O/o/D→0` and `l/I→1`, stray spaces are dropped, and 3-digit groups are joined.
   - **The ¥→4 problem.** ML Kit very often reads `¥` as `4` (`¥360` → `4360`). A bare number starting with 4 gets a second reading without the 4, as long as what remains is a valid amount (`4, 050` stays 4,050). Scoring picks the right one.
4. **`labelOf` + `classify`** (`RowKind.kt`): each row's words, with amounts and spaces removed, are matched against keyword sets to give the row its kinds:
   - `TOTAL`: 合計 plus the misreadings 合十 and 舎計, お会計, ご請求, 領収金額, お支払, 買上金額, or a bare 計. Never counts when the row is also TENDER, COUNT or TAX, so お預り合計, 合計点数 and 税額合計 are excluded.
   - `SUBTOTAL`: 小計
   - `TAX`: 税, 対象
   - `TENDER`: 預, 現金
   - `CHANGE`: 釣, the misreadings お的 and おっり, or a lone お
   - `PAYMENT`: 現計, クレジット, 取扱, デビット…
   - `COUNT`: 点数, N点, 個
5. **`TotalExtractor`** (scoring): every positive reading of every amount is a candidate. Each occurrence adds weight by row kind: TOTAL 10, PAYMENT 4, SUBTOTAL (without TAX) 3, unlabeled 1, TAX/COUNT/CHANGE/TENDER 0.2. A ¥ prefix adds 0.5, and the dropped-4 reading counts at ×0.8. Because the same total usually appears several times (小計, 対象額, クレジット…), frequency matters on its own.
6. **Cross-checks**: +6 each when a value equals `TENDER − CHANGE` (change ≥ 0), or `SUBTOTAL + TAX` where the tax is 7–11% of the subtotal. On capture6 these checks outvote a 合計 that OCR misread (130 instead of 180).
7. **Confidence**:
   - `HIGH`: the winner appears in a TOTAL row *and* passes a cross-check
   - `MEDIUM`: one of the two
   - `LOW`: neither. The total is then the **largest ¥ amount** (or the largest amount if none has ¥), e.g. meal tickets (食券), which list no 合計 and no change
   - `NONE`: no amounts found

   On the corpus: 8 HIGH, 7 MEDIUM, 2 LOW.
8. **Output**: a `ReceiptResult` with `total`, `confidence` and `rawText` (the rows joined). `subtotal`, `taxLines`, `date` and `storeName` stay empty until later goals fill them with tested values.

**Known limits / next ideas:**
- Glyph height (totals are often printed double-size) and position on the page are not used yet.
- `8%対象 + 10%対象 = 合計` and the 内税-consistency check are not implemented yet.
- No evidence is shown in the UI yet (4c).
- Weights are hand-tuned. Any change must keep the corpus at 100%.

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
- **Corpus today:** 17 real receipts (konbini, supermarkets, restaurants, a shoe shop, meal tickets, one sideways photo), passing 17/17. They are in `CorpusTest`. All personal data was deleted from the fixtures before they were committed: staff names and IDs, card details, customer numbers, transaction IDs and barcodes.
- **Test targets:**
  - Each fixture is one parameterized test. `total` must match exactly and confidence must not be NONE. `expected.json` also records `date`, which gets checked once Goal 4e implements dates.
  - There are also focused unit tests for each pipeline stage: `TotalExtractorTest` (HIGH via change, cross-checks beating a misread 合計, largest-amount fallback, no amounts), `AmountsTest` (normalization, OCR noise, ¥→4, rejecting dates and IDs) and `LayoutAndKindTest` (rows, sideways photos, labels, classification).
  - Merge gate: **100% of the corpus passes.** A new receipt that fails gets committed together with the fix, never on its own (project rule: one commit only when all tests pass).
  - The README shows corpus size as a metric.
- **Optional, later:** an instrumented test on a CI emulator that runs ML Kit on a few anonymized receipt images, to catch OCR regressions when the ML Kit version changes.
- **App JVM tests:** `OcrMappingTest` (rotation, dropping lines without text or a box) and `ScanViewModelTest` (state machine; the frame is always closed). The fake `ImageProxy` is a `java.lang.reflect.Proxy`.
- **UI (later):** Compose UI tests for the navigation Home → Scan → Result, using a fake `OcrEngine`. These need an emulator in CI.

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
   - 4a. ✅ Camera capture in memory → ML Kit OCR → raw text on the result screen, plus the debug "Export OCR" button
   - 4b. ✅ `:receipt-core` pipeline v1 (row assembly, normalization, keyword scoring, お預り − お釣り and 小計 + 税 cross-checks, confidence) plus 17 fixtures
   - 4c. Evidence on the result screen ("小計 1,185 + 税 95 = 1,280 ✓"), subtotal and tax lines in `ReceiptResult`, the remaining cross-checks
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
