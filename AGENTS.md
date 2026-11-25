# AGENTS.md — preisler architecture & working agreement

> Status: Goals 1–3, 4a, 4b, Goal 5 pass 1 and 4c (date + category) are done; v0.1.1 is released. Next: Goal 5 pass 2, then 4d.
> This file is the single source of truth for **design and the reasons behind it**. Step-by-step procedures live in `SKILLS.md`, and the user-facing summary in `README.md`. Each fact lives in one of them, and the others point to it. Any change to the architecture updates this file in the same commit.

## 1. Requirements

| ID | Requirement |
|----|-------------|
| R1 | App launches into a polished home screen with a button; the camera opens only after pressing it. |
| R2 | Scanning takes one picture **for processing only**. No image is ever written to storage. |
| R3 | Extract receipt details. The primary and hardest target is the **total price** (合計), found with full OCR + custom rules. |
| R4 | (Future) Send extracted data over the local network to a desktop application (e.g. a Python script). |
| R5 | Full release cycle on GitHub Actions; builds are installable artifacts. |

## 2. Design

```
┌──────────────────────────── :app (Android) ───────────────────────────┐
│  HomeScreen ──tap──▶ ScanScreen ──capture──▶ ResultScreen             │
│                      (CameraX, in-memory)    (total, date, category,  │
│                            │                  edits, debug export)    │
│                     ImageProxy (RAM only)            ▲                │
│                            ▼                         │                │
│                  OcrEngine ─ MlKitJapaneseOcr ─▶ OcrDocument          │
│                  (planned: ResultExporter → LAN, §6)                  │
└────────────────────────────────────┬──────────────────────────────────┘
                                     ▼
┌──────────────── :receipt-core (pure Kotlin/JVM, no Android) ──────────┐
│  OcrDocument ─▶ uprightLines ─▶ assembleRows ─┬▶ findTotal            │
│               (ReceiptReader)                 ├▶ findDate ─▶ Receipt- │
│                                               └▶ findCategory  Result │
└───────────────────────────────────────────────────────────────────────┘
```

There are two modules on purpose. `:app` holds no parsing logic, and `:receipt-core` has no Android dependencies, so it is testable on the JVM and reusable on a desktop. New modules are added only for a real need, recorded here.

| Concern | Choice | Why |
|---------|--------|-----|
| UI | Kotlin, Compose, Material 3, Navigation Compose. The 7 icons are vector drawables in `res/drawable`, with no icon library | The icon library added ~20 MB to debug builds |
| State | One `ScanViewModel`, activity-scoped and shared by Scan and Result: `Idle → Processing → Done / Failed`. `Done` keeps the extraction result untouched and adds the scan date and the user's date/category overrides; `Done.date`/`Done.category` are the effective values (override → receipt → scan date) | One-way data flow. Nothing survives process death, which is intended (R2). Overrides are in memory only and will travel with the LAN export |
| DI | Manual `AppContainer` in `PreislerApplication` | The app is too small to need Hilt |
| Camera | CameraX 1.5.1 `LifecycleCameraController` + `PreviewView`, `IMAGE_CAPTURE` only, `takePicture(OnImageCapturedCallback)`, full sensor resolution | Delivers an in-memory `ImageProxy` and never a file (R2). *Rejected:* the ML Kit Document Scanner, which returns files |
| OCR | ML Kit Text Recognition v2, bundled Japanese model `16.0.1`, behind the `OcrEngine` fun interface. `buildOcrDocument` maps results into upright coordinates | On-device, works offline, no Play Services download, returns line boxes |
| Serialization | kotlinx.serialization (`PreislerJson`) | Fixtures, debug export, and the future LAN payload |
| Build | Gradle 9.3.1, AGP 9.1.1 (built-in Kotlin), Kotlin 2.3.20, Compose BOM 2026.04.01. All versions in `gradle/libs.versions.toml` | compileSdk/targetSdk 36 until Compose 1.12, which needs 37 |
| Platforms | minSdk 26, **arm64-v8a only** (`ndk.abiFilters`), native libs stored uncompressed (the default) | ML Kit's OCR library is ~11 MB per CPU type. Shipping only arm64 took release 44.8 → 15.3 MB and debug 64.5 → 27.9 MB. Uncompressed means less storage and a faster install. Consequence: debug builds don't run on x86 emulators, so UI tests need an ARM emulator |
| Quality gates | Unit tests, Android Lint, ktlint via Spotless, privacy guard (§3), R8 release smoke build, R8 ML Kit guard | Every one fails CI |
| R8 (release) | Full mode (AGP 9 default) with keep rules for ML Kit and its gms/firebase/datatransport internals (`app/proguard-rules.pro`). CI fails if any ML Kit class is renamed or removed. Every release ships its `mapping-<version>.txt` | v0.1.0 regression: R8 optimized ML Kit's reflection- and JNI-driven internals, and every release-build scan failed with a null field read. JVM tests can't catch that, so the mapping check is the guard. The keep rules cost +0.2 MB |

**Performance notes:** debug builds show OCR time, rule time and image size on the result screen. Capture resolution stays at full 12 MP until those timings show a need to change it; lowering it is the obvious lever. `ReceiptReader` builds rows once and parses each line once. Chain matching is the costliest rule (fuzzy windows over 13 rows × ~80 names) and is still a few milliseconds.

## 3. Privacy guarantees (R2)

- The image exists only as an `ImageProxy` / `InputImage` in RAM. `ScanViewModel` closes it with `use {}` on success and on failure (`ScanViewModelTest`). Only OCR text is kept, in memory.
- The only permission is `CAMERA`, requested when the user taps scan, with an explanation if it's denied.
- ML Kit's telemetry transport declares `INTERNET` and `ACCESS_NETWORK_STATE`. Both are removed with `tools:node="remove"`, so the telemetry cannot send anything.
- **Privacy guard:** `:app:verify<Variant>PrivacyManifest` is part of `check` and CI. It fails if the merged manifest requests `INTERNET`, `ACCESS_NETWORK_STATE` or any storage/media permission. A deliberate negative test on CI proved it works. Procedure: SKILLS.md → Privacy guard.
- Backups and device transfer are disabled.
- Test fixtures contain no personal data (§5).
- The store name is only read transiently to pick a category. `ReceiptResult` has no store field (removed in schema v2), so it is never shown, kept or exported.

## 4. Extraction pipeline (`:receipt-core`) — the core problem (R3)

The input is our own `OcrDocument`: lines with text and bounding boxes. `ReceiptReader` builds the rows once (steps 1–2), then `findTotal` (steps 3–7), `findDate` (step 8) and `findCategory` (step 9) each work on them. The pipeline is deterministic and was tuned on the corpus (§5).

1. **`uprightLines`** (`Layout.kt`): when most lines are more than twice as tall as they are wide, the photo was taken sideways. The boxes are rotated (text along +y, receipt top at large x).
2. **`assembleRows`** (`Layout.kt`): ML Kit often returns the label (`合計`) and its amount (`¥1,280`) as separate lines. A line joins a row when its vertical center lies inside the row's first line and vice versa. Each row is then sorted left to right. *Fix the layout before the text.*
3. **`parseAmounts`** (`Amounts.kt`): NFKC normalization (full-width → ASCII, `￥`/`\` → `¥`). Two shapes count as money:
   - a ¥-prefixed number anywhere, where OCR's `Y` and a leading `*` also count as ¥
   - a line that is only a number plus markers (`)` `内` `軽` `※` `X`…)

   Dates, phone numbers, IDs, more than 7 digits, and leading zeros never count. Inside numbers: `O/o/D→0`, `l/I→1`, stray spaces dropped, 3-digit groups joined.
   - **¥→4:** ML Kit often reads `¥` as `4` (`¥360` → `4360`). A bare number starting with 4 also gets the reading without the 4, if what remains is a valid amount (`4, 050` stays 4,050).
4. **`labelOf` + `classify`** (`RowKind.kt`): the row's words, with amounts and spaces removed, give the row its kinds:
   - `TOTAL`: 合計, the misreadings 合十 and 舎計, お会計, ご請求, 領収金額, お支払, 買上金額, or a bare 計. Never when the row is also TENDER, COUNT or TAX, so お預り合計, 合計点数 and 税額合計 don't count
   - `SUBTOTAL`: 小計
   - `TAX`: 税, 対象
   - `TENDER`: 預, 現金
   - `CHANGE`: 釣, お的, おっり, or a lone お
   - `PAYMENT`: 現計, クレジット, 取扱, デビット…
   - `COUNT`: 点数, N点, 個
5. **Scoring** (`findTotal`, `TotalFinding.kt`): every positive reading is a candidate. Each occurrence adds weight by row kind:

   | Row kind | Weight |
   |----------|--------|
   | TOTAL | 10 |
   | PAYMENT | 4 |
   | SUBTOTAL (without TAX) | 3 |
   | unlabeled | 1 |
   | TAX, COUNT, CHANGE, TENDER | 0.2 |

   A ¥ prefix adds 0.5, and the dropped-4 reading counts ×0.8. Totals repeat across a receipt (小計, 対象額, クレジット…), so frequency matters.
6. **Cross-checks:** +6 each when a value equals `TENDER − CHANGE` (change ≥ 0), or `SUBTOTAL + tax`. The tax may come from a labeled TAX row at 7–11% of the subtotal, or from *any* row if it is exactly 8% or 10% of it (rounded down or up). These outvote a misread 合計 (fixture 0006: 130 vs the true 180) and recover totals whose labels were lost (fixture 0021: 140 + 11 = 151).
7. **Confidence:**
   - `HIGH`: the winner is in a TOTAL row and passes a cross-check
   - `MEDIUM`: one of the two
   - `LOW`: neither. The total is then the largest ¥ amount (or the largest amount at all), e.g. meal tickets (食券)
   - `NONE`: no amounts

8. **Date** (`Date.kt`): NFKC per row, top to bottom, first *real* calendar date wins (`2026年02月30日` is skipped). Formats:
   - `YYYY年M月D日`, `YYYY/M/D`, `YYYY-MM-DD`, `YYYY.MM.DD`, `令和N年M月D日` (and 令和元年)
   - meal-ticket `YYMMDD` directly before a time: OCR gives `2611 12 12:10` or `261 10,9 12;11`

   A year must be printed, so ticket validity ranges (`10月2日から`) are ignored. No legible date → null, and the app shows the scan date instead (§2, State).
9. **Category** (`Category.kt`), one of SUPERMARKET, KONBINI, DRUGSTORE, RESTAURANT (incl. cafés, meal tickets), FOOD_SHOP (bakery, sweets, greengrocer), RETAIL (clothes, shoes, homeware, variety), LEISURE (tickets, parks, tourist shops), TRAVEL (rail, bus, taxi, parking, fuel, hotels), OTHER. The rule is to categorize by the *store*, not by what was bought.
   1. **Chains** in the first 8 or last 5 rows (store names sit there, and product names like ライオン in the middle must not hit イオン). Matching is fuzzy on *folded* text: NFKC, upper case, 口→O, separators dropped, dakuten/handakuten dropped (ガ→カ, プ→フ). It allows 0 edits for names ≤ 3 chars, 1 for ≤ 7 and 2 for longer, so `LAWB口N`, `セプン-イレフン` and `チイリーヤマザキ` still match.
   2. **Keyword cues** anywhere: travel (乗車券, 特急, タクシー, 駐車…), drugstore (薬局, 調剤…), restaurant (定食, テーブ, 人数, 伝票, (並)…), food shop, leisure (観光, パーク, 入場…). Most hits wins, ties go to that order.
   3. **Tax fallback:** only 10% and no 軽減 marker → RETAIL (food would be 8%). Otherwise OTHER.
10. **Output:** a `ReceiptResult` (schema v2) with `total`, `confidence`, `date`, `category` and `rawText`. `subtotal` and `taxLines` are reserved for 4d.

   On the corpus: 23/23 totals, dates and categories. Confidence: HIGH, MEDIUM or LOW, never NONE.

**Known limits:** glyph height and page position aren't used. Missing checks: `8%対象 + 10%対象 = 合計` and 内税 consistency. No evidence is shown in the UI yet (4d). The weights are hand-tuned. Chain lists only cover chains seen so far plus the big national ones; unknown stores rely on cues. The cue scores are close on some receipts (fixture 0016: restaurant 2 vs leisure 1).

## 5. Testing & test targets

- **Corpus:** `receipt-core/src/test/resources/receipts/<NNNN>-<store>/` holds `ocr.json` (debug export from a real device), `expected.json` (reference values read from paper by the user) and `source.md`. Today there are 23 receipts: konbini, supermarkets, drugstores, restaurants, food shops, shops (shoes, homeware, discount), tourist spots, meal tickets, a rail pass, one sideways photo. Personal data was deleted before the first commit (procedure: SKILLS.md → Add a receipt fixture).
- **Targets:**
  - `CorpusTest`: one parameterized test per receipt. `total`, `date` (null where not legible) and `category` must match exactly, and confidence must not be NONE. Categories are labeled by the user. A size guard catches a missing corpus.
  - **Merge gate: 100% of the corpus.** A failing new receipt is committed together with its fix.
- **Stage tests:**
  - `TotalTest`: HIGH via change, cross-checks beating a misread 合計, exact tax on an unlabeled row, largest-amount fallback, no amounts
  - `DateTest`: every format, meal tickets, invalid dates skipped, no year → null
  - `CategoryTest`: OCR-mangled chains, header/footer only, each cue category, tax fallback, folding
  - `AmountsTest`: normalization, OCR noise, ¥→4, rejecting dates and IDs
  - `LayoutAndKindTest`: rows, sideways photos, labels, classification
  - `ModelSerializationTest`
- **App JVM tests:**
  - `OcrMappingTest`: rotation, dropped lines
  - `ScanViewModelTest`: state machine, the frame is always closed, timings measured, scan date stands in for a missing date, overrides win without changing the extraction. The fake `ImageProxy` is a `java.lang.reflect.Proxy`
- **Later:** Compose UI tests and an instrumented ML Kit regression test. Both need an **ARM** emulator (§2, Platforms).

## 6. CI, release, and the future LAN export

- `ci.yml` runs on every push and covers:
  - format, core and app tests, lint, the privacy guard and the permission printout
  - the debug APK artifact
  - an unsigned R8 release build
  - an APK size report

  `release.yml` runs on a `v*` tag or **Run workflow** with a version. It builds a signed, minified APK and publishes a GitHub Release. The version name comes from the tag and the version code from the run number. Procedures: SKILLS.md → Verify a change / Cut a release.
- The agent workspace can't build Android and can't push tags, so CI is the build machine and the user starts releases.
- **LAN export (R4), planned:**
  - A `ResultExporter` interface in `:app` (`suspend fun export(result: ReceiptResult)`), with a `LanExporter` that discovers the desktop via Android NSD (`_preisler._tcp`).
  - The desktop Python script advertises with `zeroconf` and serves `POST /receipts`, which takes the `ReceiptResult` JSON. A one-time pairing code blocks unwanted senders.
  - `schemaVersion` keeps the contract stable. This adds the INTERNET permission, so update §3, the privacy guard and the README together.

## 7. Roadmap

1. ✅ Repo, README, MIT license
2. ✅ Architecture plan
3. ✅ Skeleton app, CI with debug APK artifact, release workflow
4. Core loop:
   - 4a. ✅ In-memory capture → ML Kit OCR → result screen, debug Export OCR
   - 4b. ✅ Pipeline v1 with cross-checks and confidence, 17 fixtures, largest-amount fallback
   - 4c. ✅ Date (scan date as fallback, editable) and category (chains + cues + tax fallback, editable), schema v2 without a store name, 23 fixtures, exact-tax cross-check
   - 4d. Evidence on the result screen ("小計 1,185 + 税 95 = 1,280 ✓"), subtotal and tax lines in `ReceiptResult`, the remaining cross-checks
   - 4e. Harder receipts (thermal fade, skew, 内税 vs 外税, multiple tax rates)
   - 4f. LAN export + Python desktop receiver
5. After each 4x step, a refactor/simplify pass. Pass 1 (after 4b): arm64 only, icon library replaced, single-pass parsing, dead code removed, OCR timings in debug, docs deduplicated.

## 8. Agent working rules

- One goal at a time. Planning gates need the user's OK.
- Commit to `main` only when everything passes on CI.
- Update `AGENTS.md`, `SKILLS.md` and `README.md` in the same commit as the change they describe.
- Ask when unsure. Never invent expected values for fixtures.
- Privacy first. Personal data never enters a commit. If it does, rewrite history immediately.
- Commit trailers: see the session attribution lines.
