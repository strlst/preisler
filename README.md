# preisler

**preisler** is an Android app, written in Kotlin, that scans Japanese receipts (レシート) and pulls out the financial information on them. The main goal is to find the **total price** (合計) reliably.

## Purpose

Japanese receipts are dense and printed in many layouts. They mix full-width and half-width characters and list several totals, such as 小計 (subtotal), 合計 (total), お預り (amount tendered), お釣り (change), and tax breakdowns at 8% and 10%. Reading the right number by hand is tedious. preisler aims to:

- Open on a home screen with a clear call to action instead of dropping you straight into the camera.
- Take one picture for processing only. **No images are saved** to the device.
- Run OCR on the picture and apply rules made for Japanese receipts to find the total (and, later, other details such as date, store and tax).
- Later, send the results over the local network to a desktop companion (for example a small Python script). The architecture is designed with this in mind from the start.

## Privacy

preisler is built so receipts stay private:

- **No pictures are stored.** The camera hands the photo to the app in memory. It is read by on-device OCR and then discarded. Nothing is written to storage, the cache or the gallery.
- **No storage permission.** The app cannot read or write your files.
- **On-device OCR.** Text recognition uses ML Kit's bundled Japanese model and runs fully offline. Receipt contents never go to a cloud service.
- **No internet permission.** The app cannot talk to the internet at all. The only permission it asks for is the camera.
- The planned desktop link (see below) will be **local network only**. It will be opt-in and protected by a pairing code. When it lands, that will be documented here.

Automated tests check the merged Android manifest, so these claims can't quietly regress.

## Architecture

The app is split into two modules:

| Module | What it contains |
|--------|------------------|
| `:app` | Android only: the Compose UI, CameraX capture and the ML Kit OCR adapter |
| `:receipt-core` | Pure Kotlin with no Android dependencies: everything that turns OCR text into a total (row rebuilding, text normalization, keyword scoring, cross-checks such as 小計 + 税 = 合計) |

Why split it this way:

- **Fast, reliable tests.** The hardest part, finding the right total, runs as plain JVM unit tests against a growing set of real receipts in seconds, with no emulator needed. Every receipt in the set must pass before a change is merged.
- **Swappable OCR.** The core logic only sees its own `OcrDocument` model, so ML Kit could be replaced or upgraded without touching the extraction rules.
- **Reusable on the desktop.** The same core and result format (versioned JSON) can be used by a desktop tool or a CLI. This makes the planned LAN export and its desktop counterpart straightforward.
- **Clear boundaries.** UI and camera code can't creep into the parsing logic, which keeps it deterministic and easy to change.

See [`AGENTS.md`](AGENTS.md) for the full design.

## Release cycle

The project is built and released with GitHub Actions CI. Each build produces an installable APK as a downloadable artifact.

## Status

🚧 Early stage. The architecture is approved and the skeleton app builds on CI: a home screen and a placeholder scan screen. Camera capture and OCR come next.

**Try it:** open [Actions](https://github.com/strlst/preisler/actions/workflows/ci.yml), pick the latest green run on `main`, download the `preisler-debug-…` artifact, and install the APK. Tagged releases (`v*`) publish signed APKs under [Releases](https://github.com/strlst/preisler/releases).

## Note: an experiment in fully agentic engineering

This repository is an experiment in **fully agentic software engineering**. An AI agent (Claude) does the planning, architecture, code, tests, CI setup and documentation. A human sets goals, supplies test data (photos of real receipts) and approves each milestone. Work moves forward one goal at a time:

1. Create the repository and README *(this step)*
2. Agree on a detailed architecture plan
3. Build a skeleton app on CI with installable artifacts
4. Implement core features step by step, each with clear test targets and reference outputs
5. Refactor and simplify after each feature to keep technical debt low

The current architecture and agent working notes are kept in `AGENTS.md` and `SKILLS.md`.

## License

Released under the [MIT License](LICENSE).
