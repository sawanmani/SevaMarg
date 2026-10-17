# NOTICES — licence and attribution ledger

Rule: nothing enters the stack without a row here, and **code licence ≠ model-weight licence**.
Anything marked `TO VERIFY` must be checked by a human on the upstream repo/HuggingFace card
before it is named on a slide. Slide 9 and the synopsis both reference this file.

> **Platform note:** the stack is Android (Kotlin/Compose) + Node tooling for the catalogue/data
> pipeline. Rows for the earlier PWA plan (Vite/React/Workbox/Dexie/axe-core/FastAPI bridge) were
> removed on 17.10.2026 when the platform changed — see `docs/decisions/0002-native-android-over-pwa.md`.

| Component | Where used | Claimed licence | Weight/model licence | Status | Attribution text in UI |
|---|---|---|---|---|---|
| Kotlin / JDK 17 | all app code | Apache-2.0 (compiler) | — | ✅ | — |
| **Jetpack Compose + Material 3** | `:app` UI | Apache-2.0 | — | ✅ | credits screen |
| **AGP / Gradle** | build | Apache-2.0 | — | ✅ | — |
| Room, DataStore, WorkManager, `kotlinx.serialization`, `kotlinx-coroutines` | `:app`/`:core` | Apache-2.0 | — | ✅ | credits screen |
| Vosk **Android** SDK (`alphacephei/vosk-android`) | `:voice` on-device ASR | Apache-2.0 | `vosk-model-hi-*` model card — **TO VERIFY** (Kaldi-derived) | ⚠️ | About screen |
| **sherpa-onnx** (if Piper TTS is chosen) | `:voice` TTS runtime | Apache-2.0 (verify per release) | Piper voice cards vary (CC-BY / BSD) — **TO VERIFY per voice** | ⚠️ | About screen |
| **Piper** voices (`rhasspy/piper` model zoo) | TTS audio assets | MIT (code) | per-voice model licence | ⚠️ | About screen |
| **eSpeak NG** | TTS fallback only | **GPL-3** ⚠️ | — | ⚠️ **licence decision pending ADR-0003** — must be a separate process/asset, not linked; or dropped | About screen if used |
| Android `TextToSpeech` framework | optional TTS engine path | platform | — | ✅ | — |
| TalkBack (Google) | a11y test target | proprietary, user-installed | — | ✅ | tested-with mention only |
| Espresso / UI Automator / Robolectric / `ComposeAccessibilityChecks` | tests | Apache-2.0 | — | ✅ | — |
| Node 20 + TypeScript | `packages/catalogue`, `packages/i18n`, eval-set build | Apache-2.0 | — | ✅ | — |
| ajv (JSON Schema validation) | `validate.mjs` | MIT | — | ✅ | — |
| yaml (js-yaml) | catalogue loader | MIT | — | ✅ | — |
| **AI4Bharat IndicTrans2** | offline catalogue content prep only, human-reviewed | code **TO VERIFY** / weights on HF | **TO VERIFY** (attribution / usage terms) | ⚠️ | methodology note in deck |
| OpenStreetMap data (pre-bundled extract) | office locator | — | data **ODbL** — attribution mandatory on screen | ✅ must show | "© OpenStreetMap contributors" on Office screen |
| Overpass API / `tools/osm/extract.py` | build-time extract only (not at runtime) | ODbL/ODC-DbSL for the API | — | ✅ | same attribution |
| ServicePlus / Service Online (eGov Foundation) | architecture precedent, cited on slide 9 | open source — **TO VERIFY exact repo licence** | — | ⚠️ | citation only |
| pandas | bench/field tooling | BSD-3 | — | ✅ | — |

## Before 6 Nov submission (Phase 4 checklist)

- [ ] Every ⚠️ row resolved by reading the actual upstream licence text, screenshot in `docs/proof/sources/licences/`
- [ ] **eSpeak NG GPL-3 question closed in ADR-0003** (used-as-separate-component vs dropped); if in use, note the obligation in this file
- [ ] Vosk Hindi model-weight licence read and quoted in this file (size + version + hash of the shipped model recorded here)
- [ ] ODbL attribution visible in the Office screen and in the deck screenshot
- [ ] If any weight licence forbids the use we describe, we **remove the claim from the slide**, not the row
- [ ] Third-party trade marks (e-District, CSC, Android, Google TalkBack) appear only as factual
      references, never as endorsements — plus the "not a government platform" disclaimer from BLUEPRINT §7
- [ ] `LICENSE` file chosen for our own repo (suggest MIT or Apache-2.0 to stay compatible with the
      above), stated in README so a department can actually reuse it
