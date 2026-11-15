# preisler

**preisler** is an Android app, written in Kotlin, that scans Japanese receipts (レシート) and finds the **total price** (合計). Everything happens on the phone.

Japanese receipts are dense, mix full- and half-width characters, and list several amounts that look like a total: 小計 (subtotal), 合計 (total), お預り (tendered), お釣り (change), and 8% and 10% tax lines. preisler reads the receipt with on-device OCR and uses rules written for Japanese receipts, including the receipt's own arithmetic, to pick the right number.

## Status

🚧 Early stage. You can scan a receipt and get its total with a confidence rating. Date, store and tax details come later, and so does sending results to a desktop app over the local network.

**Accuracy on the test corpus:** 17 / 17 real receipts (konbini, supermarkets, restaurants, shops, meal tickets). Every change must keep this at 100%.

**Try it:** signed APKs are under [Releases](https://github.com/strlst/preisler/releases). Development builds come from the latest green [CI run](https://github.com/strlst/preisler/actions/workflows/ci.yml) on `main` (the `preisler-debug-…` artifact). The app runs on 64-bit ARM phones with Android 8.0 or newer.

## Privacy

- **No pictures are stored.** The photo stays in memory, is read by on-device OCR, and is discarded. Nothing is written to storage, the cache or the gallery.
- **On-device OCR.** ML Kit's bundled Japanese model runs fully offline. Receipt contents never go to a cloud service.
- **No internet and no storage permission.** The only permission is the camera. Google's ML Kit telemetry is cut off as well, because its network permissions are removed from the app.
- **No backups.** The app keeps no data, and Android backup and device transfer are disabled.
- The planned desktop link will be **local network only**, opt-in, and protected by a pairing code. This section will be updated when it lands.

The build fails if the app's merged manifest ever requests network or storage access, so these claims can't quietly regress.

## Architecture

| Module | What it contains |
|--------|------------------|
| `:app` | Android only: the Compose UI, CameraX capture, the ML Kit OCR adapter |
| `:receipt-core` | Pure Kotlin, no Android: everything that turns OCR text into a total (row rebuilding, text clean-up, keyword scoring, cross-checks such as お預り − お釣り = 合計) |

Why it's split this way:

- **Fast, reliable tests.** Finding the right total, the hard part, runs as plain JVM tests against real receipts in seconds, with no emulator.
- **Swappable OCR.** The rules only see preisler's own `OcrDocument` model, so ML Kit can be upgraded or replaced without touching them.
- **Reusable on the desktop.** The same core and its versioned JSON result can power a desktop tool or a CLI, which keeps the planned LAN export simple.
- **Clear boundaries.** UI and camera code can't creep into the parsing logic, so it stays deterministic and easy to change.

The full design is in [`AGENTS.md`](AGENTS.md).

## An experiment in fully agentic engineering

An AI agent (Claude) does the planning, architecture, code, tests, CI and documentation. A human sets goals, supplies test data (real receipts) and approves milestones. Work moves one goal at a time: repository → architecture plan → skeleton app on CI → features, each with test targets and reference outputs, followed by a refactor pass. Agent notes are in [`AGENTS.md`](AGENTS.md) (design) and [`SKILLS.md`](SKILLS.md) (procedures).

## License

Released under the [MIT License](LICENSE).
