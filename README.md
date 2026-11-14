# preisler

**preisler** is an Android app, written in Kotlin, that scans Japanese receipts (レシート) and pulls out the financial information on them. The main goal is to find the **total price** (合計) reliably.

## Purpose

Japanese receipts are dense and printed in many layouts. They mix full-width and half-width characters and list several totals, such as 小計 (subtotal), 合計 (total), お預り (amount tendered), お釣り (change), and tax breakdowns at 8% and 10%. Reading the right number by hand is tedious. preisler aims to:

- Open on a home screen with a clear call to action instead of dropping you straight into the camera.
- Take one picture for processing only. **No images are saved** to the device.
- Run OCR on the picture and apply rules made for Japanese receipts to find the total (and, later, other details such as date, store and tax).
- Later, send the results over the local network to a desktop companion (for example a small Python script). The architecture is designed with this in mind from the start.

## Release cycle

The project is built and released with GitHub Actions CI. Each build produces an installable APK as a downloadable artifact.

## Status

🚧 Early stage. The architecture plan is in progress and no app code exists yet.

## Note: an experiment in fully agentic engineering

This repository is an experiment in **fully agentic software engineering**. An AI agent (Claude) does the planning, architecture, code, tests, CI setup and documentation. A human sets goals, supplies test data (photos of real receipts) and approves each milestone. Work moves forward one goal at a time:

1. Create the repository and README *(this step)*
2. Agree on a detailed architecture plan
3. Build a skeleton app on CI with installable artifacts
4. Implement core features step by step, each with clear test targets and reference outputs
5. Refactor and simplify after each feature to keep technical debt low

The current architecture and agent working notes are kept in `AGENTS.md` and `SKILLS.md`.

## License

To be decided.
