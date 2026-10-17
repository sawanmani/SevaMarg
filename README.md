# Seva-Marg · सेवा-मार्ग

> **UP State Innovation Challenge 2026 (SFIC / Seva Sankalp Abhiyaan)**
> Problem Statement: **UPNIC-004 — Digital Governance for Citizen Services** (IT & Electronics Department)

**An offline-first, voice-first, TalkBack-first Android app** for citizen services in Uttar
Pradesh — built in Kotlin + Jetpack Compose, with on-device Hindi speech (no cloud, no laptop).

> **Tagline (hi):** सेवा-मार्ग — सरकारी काम, अपनी आवाज़ में।
> **One-line (en):** Seva-Marg — know the documents, office, fee and timeline for a UP
> certificate, by voice, in Hindi, with no internet and no agent.

Ek sawal bolo — *"आय प्रमाण पत्र कैसे बनता है?"* — app bolta hai: kaun-se document, kaun-se
office, kitni fees, kitne din, aur aage kya hoga. Sab Hindi mein, radio band, phone ke andar.

**Demo move no other entry can copy:** finale table par QR code — judges **apne khud ke phone
par APK install karke** Hindi me test karte hain.

---

## Statement (verbatim, from the portal — this is our rubric)

```
title    : Digital Governance for Citizen Services
department: IT & Electronics Department
brief    : Many residents still struggle to find the right service, complete a form,
           or know what happens next.
scope    : Reduce steps for a real public service.
           Work in Hindi and English.
           Be usable on a basic smartphone.
outcome  : Faster, clearer access to a government service for people across the state.
```

## Promise table — every scope bullet gets a measured proof

| Scope bullet | Our promise | Evidence artefact | Where it lives |
|---|---|---|---|
| Reduce steps for a real public service | Median citizen journey: **N steps → M steps** (measured, not claimed) | Step-count benchmark, n≥25 walk-throughs | `tools/bench/stepcount.py`, `results/stepcount/` |
| Work in Hindi and English | Same answer, same accuracy, both languages — parity-tested | 50 HI + 50 EN question set, per-language accuracy | `evals/`, `tools/bench/qa_accuracy.py` |
| Be usable on a basic smartphone | Sideloadable APK running on a ~₹8–10k Android (2 GB RAM, Android Go), cold start < 2 s, **no laptop in the loop** | Install matrix + cold-start log + airplane-mode run | `results/device-matrix/` |

Every answer shows **source + last-verified date**. No source → the app refuses to guess and
routes to the nearest office instead. That refusal path is a feature, and we will say so in the defense.

---

## Status

| Item | State |
|---|---|
| Problem statement | **Locked: UPNIC-004** (Track A — Predefined Problem Statement) |
| Platform | **Android (Kotlin + Jetpack Compose)** — not a PWA, not iOS |
| Team category | College / University |
| Registration + PS lock | **Due TODAY — 17.10.2026** (portal blocks after this; selection is permanent) |
| Synopsis (abstract + public demo video URL + PDF ≤ 20 MB, not editable after submit) | Due **06.11.2026** |
| Finalists | 23.11.2026 |
| Grand Finale (physical prototype + live defense) | **02.12.2026** |

## Read in this order

1. [`docs/BLUEPRINT.md`](docs/BLUEPRINT.md) — product definition, architecture, offline + voice model, data model, safety/accessibility design
2. [`docs/PROJECT_STRUCTURE.md`](docs/PROJECT_STRUCTURE.md) — repo layout and what belongs where
3. [`docs/PHASES.md`](docs/PHASES.md) — Phase 0→6 with hard exit gates
4. [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — day-level backlog, owners, Definition of Done
5. [`docs/EVIDENCE_AND_DEMO.md`](docs/EVIDENCE_AND_DEMO.md) — benchmarks, field-study instrument, deck skeleton, demo runbook, Q&A bank

## Reproduce in 5 commands (target state, Phase 1+)

```bash
npm ci && npm run validate:catalogue        # 1. data contract + citations (fails on any uncited field)
npm run catalogue:build                     # 2. YAML → android/core/src/main/assets/catalogue.json
./gradlew :core:test                        # 3. intent / composer / refusal unit tests — no device needed
./gradlew :app:assembleDebug                # 4. APK
adb install -r artifacts/seva-marg-full-debug.apk && ./scripts/demo-prep.sh   # 5. on the phone, airplane mode
```

`scripts/bench-all.sh` then regenerates every number in the deck from the installed build.

## Seed data already in the repo

- `packages/catalogue/schema/service.schema.json` — the citation-required catalogue contract
- `packages/catalogue/data/income-certificate.yaml` — template; **all values marked `VERIFY_OFFICIAL`**
- `evals/questions_hi.sample.yaml`, `evals/questions_en.sample.yaml`, `questions_oos.sample.yaml`

The catalogue stays a **Node/TypeScript data package**; it compiles to
`android/core/src/main/assets/catalogue.json`, which is what the app ships inside the APK.
No runtime JSON fetch, no server, no CDN — the asset *is* the offline story.

> ⚠️ **Fabrication ban.** No fee, document, office or SLA value enters the catalogue without
> `source_url` + `verified_on` from an official page (e-District UP, district `.nic.in` service
> pages, UP CSC/State e-Gov dept. citizen charter). Until verified, the field stays `VERIFY_OFFICIAL`.
> A judge can check any answer in one click — that is our differentiation.
