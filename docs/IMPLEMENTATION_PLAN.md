# Implementation Plan — day level, four roles, Android

**Assumptions stated once, not re-asked:** 4 members, one budget Android test phone (₹8–10k,
2 GB RAM, ideally Android Go) + one mid-range, team comfortable in React/TypeScript + some Python,
**Kotlin learning on the job** (mitigated by keeping `:core` a plain JVM module and the UI at 5
routes with Material 3 defaults). Nothing here requires a server, a domain, or a Play Store account.

---

## 1. Roles

| ID | Role | Owns | Must ship (definition of done) |
|---|---|---|---|
| **M1** | Tech lead / `:core` | Gradle setup, catalogue→Kotlin models, intent router, answer composer, refusal router, `:bench`, CI, ADRs | `./gradlew :core:test` green with ≥ 35 tests; `:bench` emits results JSON; no user-visible fact hardcoded in Kotlin |
| **M2** | Android UI + accessibility | `:app` Compose screens, semantics/live region, theme/type scale, voice-only mode, Room history, About/licence screen, `androidTest` a11y checks | J1+J4+journey screens on device; accessibility checks 0 errors; TalkBack walkthrough log exists |
| **M3** | Content + field research | source hunt & screenshots, YAML catalogue, HI/EN twins, `district_overrides`, FAQ set, field study recruiting + 25 sessions, CSC interview | 100 % fields cited with dated screenshots; step baseline by hand; field CSV + consent log |
| **M4** | Voice + evidence | `:voice` (Vosk ASR, TTS engine), utterance recordings, `tools/bench/*`, device matrix, perf + netcheck, deck charts, `verify-claims.sh`, APK releases/QR | ≥ 85 % on-device intent accuracy logged; 6 benchmark outputs in `results/`; every deck numeral regenerable |

Shared rules: nobody merges without `:core:test` + validate green; every claim-bearing number is
produced by a script; **M3 never touches code, M2 never edits the catalogue** (that separation is
what keeps the data honest).

---

## 2. Week 0 — today, 17.10 (Phase 0)

| Who | Task | Time |
|---|---|---|
| All | Registration + PS lock **UPNIC-004** (College/University · Better Public Services → Digital public services · paste the 283-char Android idea text from `PHASES.md §Phase 0`) | 30 m |
| M1 | Create repo, commit `docs/`, paste Team ID + PS-locked screenshot into `docs/proof/portal/` | 15 m |
| M2 | Order/arrange the budget test phone (if none exists: **this is the day's real risk** — a ₹7k used Redmi/Infinis with Android 12 is enough) | 30 m |
| M3 | List the official sources to verify (e-District UP service pages, district `.nic.in`, UP CSC citizen charter) and book 5 field-respondent conversations for next week | 45 m |
| M4 | `adb` + Android Studio install starts downloading now (it is 3–5 GB; do not let it eat tomorrow) | background |

---

## 3. Week 1 — 18–21.10 (Phase 1: spike + catalogue)

| Day | M1 | M2 | M3 | M4 |
|---|---|---|---|---|
| **18.10** | Gradle multi-module scaffold, version catalog, `:core` JVM + first 2 tests, `.editorconfig`, git hygiene | Android Studio comfortable: run the scaffolded app on device **by end of day** (sideload + "Install via USB" on MIUI/Oppo handled once) | Start source hunt: income certificate (online + CSC channels) | `vosk-android` dependency in `:voice`, model download, hello-world transcribe 1 wav on **desktop JVM** first (no device variable yet) |
| **19.10** | `service.schema.json` locked + `validate.mjs` failing on missing `source_url`/`VERIFY_OFFICIAL`; `build.mjs` writes `core/src/main/assets/catalogue.json` | Compose: nav rail of 5 routes, AskScreen text input + answer card shell, type scale + contrast tokens | Fill `income-certificate.yaml` ~60 %, screenshots named by date | **Spike day:** Vosk-Android on the budget phone, 5 real Hindi utterances, WER + intent accuracy + init time + RAM → `results/voice-spike/` |
| **20.10** | Intent router v0 in `:core` + 10 tests (Devanagari + Hinglish normalisation) | Answer card semantics (`liveRegion`), SourceChip, StalenessBanner | Finish income certificate fields; hand-count `channel[].steps` baseline (2 channels) | TTS shoot-out: Piper-via-sherpa-onnx vs eSpeak NG vs Android TTS on the phone — listen test with the team, log sizes + first-audio latency |
| **21.10** | Refusal router v0 + `:bench` skeleton; ADR-0002 (native over PWA) + ADR-0003 (voice engines) written | Voice-only UI state machine stub (no audio yet); CI `android.yml` (`:core:test`, `assembleDebug`) | Begin residence certificate; ambiguity log (2 official pages disagreeing = FAQ row + deck finding) | **Gate review**; if APK/Vosk failed, invoke the fallback now (see §6) not later |

**Gate 1 = PHASES.md Gate 1.** Do not start Phase 2 features until the spike has a number attached.

---

## 4. Week 2 — 22–27.10 (Phase 2: J1/J4/J5 on a phone, accessible)

| Day | M1 | M2 | M3 | M4 |
|---|---|---|---|---|
| **22.10** | Answer composer typed `Answer` + golden JSON tests | JourneyScreen (documents → fees → office → SLA → next) | Residence YAML; birth YAML skeleton | `perf.py` using `adb shell am start -W`; device matrix template |
| **23.10** | Staleness + `district_overrides` merge; serialization test on the real generated asset | ChecklistScreen field explainer (speakable, example values) | Verify residence values against a second official page | Instrumentation test harness (`J1HindiTextTest`) running in CI emulator |
| **24.10** | Refusal router hardening: confidence floor, OOS topics, 15 evals through `:bench` | About screen (disclaimer, sources, ODbL, "clear my data" button wired to Room) | Office-type list + helpline numbers **from official pages only** | `netcheck.py`: airplane-mode socket capture; prove 0 external requests |
| **25.10** | `:core` coverage pass; fix the 3 worst edge cases found by M3's question list | `J4RefusalTest` + `J5AirplaneModeColdStartTest`; D-pad focus pass | **Field study days 1–2** (8–10 respondents) | Record the 20 utterances (city/Awadh/Purvanchal/Bhojpur) with consent |
| **26.10** | Performance: cold-start regression if > 2 s (defer asset parse off the main thread) | a11y: `ComposeAccessibilityChecks` green on all 5 routes; TalkBack walkthrough recorded | Field days 3–4 (to 25) + CSC operator interview | `asr_intent.py` first full run on the phone; publish the failure list |
| **27.10** | **Gate 2 review**; freeze `:core` API surface so Week 3 doesn't churn it | Device matrix completed on both phones | Field CSV cleaned (`ingest.py`), step-delta chart v1 | `results/` folder tidied; `verify-claims.sh` running against the draft claims file |

**Gate 2 = J1 + J4 + J5 pass in airplane mode on the budget phone, accessibility clean, 0 sockets.**
This is the submission-safe state. **If Phase 3 never happened, we could still submit today.**

---

## 5. Week 3 — 28.10–01.11 (Phase 3: on-device voice + parity)

| Day | M1 | M2 | M3 | M4 |
|---|---|---|---|---|
| **28.10** | `AsrEngine` interface in `:core` (text in/out only); `:voice` implements it | Mic button (long-press, haptics, waveform), transcript display | EN twin content for income + residence | Vosk init on first press + progress state; measure init ms |
| **29.10** | Latency instrumentation: logcat timestamps through capture→decode→intent→compose→TTS | Voice state machine wired to the answer region; interrupt-on-next-press | 50 EN eval items finalized (`questions_en.yaml`) | `TtsEngine` impl of the ADR-0003 choice; first-audio ms logged |
| **30.10** | Garbled-transcript tests: ASR noise must land in refusal, never a guess | Voice-only mode (visuals hidden) + screen-off operability test | EN parity gap-fix | `qa_accuracy.py` for HI + EN → parity table (Δ ≤ 2 pp) |
| **31.10** | `tools/osm/extract.py` → `offices-<district>.json`; haversine in `:core` | OfficeScreen with bundled data + ODbL attribution | Office hours/addresses cross-checked with 1 real phone call per office | Full voice J1 in airplane mode, 20 runs, median + p95 |
| **01.11** | **Gate 3 review** + evidence freeze list | Bug-fix pass on a11y regressions from voice states | Deck content list: which number lives on which slide | `results/asr/` + `results/qa/` + `results/refusal/` final; lite-APK experiment (Phase 5 seed) |

---

## 6. Fallback ladder if the voice spike fails on 19–21.10

Decide **on 21.10 with the number in hand**, not in November with adrenaline:

| Situation | Action | Deck language |
|---|---|---|
| Vosk intent accuracy ≥ 85 % | Continue as planned | "on-device Hindi voice, measured" |
| 70–85 % | Keep ASR; add quick-reply chips as the primary voice follow-up path; demo with recorded utterances replayed to the phone | "85 % after we added X; here is the failure list" |
| < 70 % on real accents | Ship V0 text + chips, keep ASR behind an "experimental" toggle | "voice is experimental in v1; the text path is what 25 citizens used" |
| Piper/sherpa TTS won't integrate | eSpeak NG (robotic, offline) or drop TTS and keep ASR + on-screen text read by TalkBack | "output is spoken by the screen reader the user already owns" — an honest and arguably better a11y answer |
| Both fail | Text-only app, offline, accessible, cited | The statement never says voice. It says *Hindi and English* and *basic smartphone*. Those are still fully met — and no judge can call an offline Hindi app with citations a weak entry |

The one thing we never do: claim a voice path that needs the internet.

---

## 7. Week 4 — 02–06.11 (Phase 4: evidence, deck, video, SUBMIT)

| Day | Who | Task |
|---|---|---|
| **02.11** | M4 + M3 | `scripts/bench-all.sh` end-to-end; every `results/` file regenerated from the **release** APK build number |
| **03.11** | All | **Evidence freeze.** Numbers stop moving. Anything not measured today is reported as "not tested" |
| **04.11** | M2 + M3 | Deck (11 slides, `EVIDENCE_AND_DEMO.md §5`) → PDF < 20 MB; synopsis text from `SYNOPSIS_DRAFT.md` with placeholders replaced by frozen numbers |
| **05.11** | M1 + M4 | 3-minute video, 8 segments, no cuts inside a demo segment, airplane indicator visible; upload → get the **public/unlisted URL working on a fresh device** |
| **06.11 by 12:00** | M1 | Submit abstract + video URL + PDF. Screenshot the confirmation into `docs/proof/portal/`. Repo tagged `v1.0-submission` |

Afternoon of 06.11 is intentionally empty. Submission is a morning act.

---

## 8. Phase 5 / 6 (07–20.11, 23.11–02.12)

| Task | Owner | Gate |
|---|---|---|
| 5.1 Lite APK (text-only, no models) | M4 | < 20 MB, installs on the Go phone |
| 5.2 Analytics CSV via SAF | M1 | export opens in a spreadsheet, no PII columns |
| 5.3 Timed scale proof: 4th service + 1 district override, **< 30 min, zero Kotlin change** | M3 (drives) + M1 (timer) | screen recording of the clock + the new APK working |
| 5.4 Public repo + 90-second tour video | M2 | README 5-command reproduce path works on a clean clone |
| 5.5 PwD usability test (2–3 users, consent, real APK + TalkBack) | M2 + M3 | report n and method, or don't report it |
| 5.6 GitHub Release + APK hash + printed QR | M4 | a phone not owned by the team installs it from the QR |
| 5.7 Pilot letter to IT & Electronics / UP e-Gov (CSC) | M3 | drafted, signed, sent-or-ready by 18.11 |
| 6.1–6.5 Finale rehearsal ladder | All | 8-minute slot, zero network, twice, no restart; two people can each run it alone |

---

## 9. Definition of done (project-wide)

1. A judge can install the APK on their own phone and complete J1 in Hindi, airplane mode, in
   under 60 seconds — with no team member touching the device.
2. Every user-visible fact traces to `source_url` + `verified_on` + a dated screenshot.
3. Every number in the deck and synopsis regenerates from `scripts/verify-claims.sh`.
4. The refusal path is demoed, not described.
5. Limitations are stated by us, before the judges find them.

---

## 10. Dependency graph

```
             ┌──────────────────┐
             │ M3: verified     │
             │ catalogue (YAML) │
             └────────┬─────────┘
                      │ build.mjs → assets/catalogue.json
             ┌────────▼─────────┐
             │ M1: :core        │  intent · compose · refusal · staleness
             │ (pure Kotlin)    │
             └───┬──────────┬───┘
                 │          │
      ┌──────────▼──┐    ┌──▼───────────────┐
      │ M2: :app    │    │ M4: :bench       │
      │ Compose+a11y│    │ (JVM evals)      │
      └──────┬──────┘    └──┬───────────────┘
             │              │
   ┌─────────▼───────┐      │  results/qa, refusal
   │ J1/J4 on device │◄─────┘
   └─────────┬───────┘
             │        ┌───────────────────────┐
   ┌─────────▼────┐   │ M4: :voice (Vosk+TTS) │  ← parallel branch, never blocks submit
   │ J5 airplane  │   └──────────┬────────────┘
   │ cold start   │              │ ≥85 % on phone
   └─────┬───────┘      ┌────────▼────────────┐
         │              │ J1 by voice (HI)    │
         └──────────────┴────────┬────────────┘
                                 │
                    ┌────────────▼──────────────┐
                    │ M4+M3: evidence + field   │
                    │ → deck → video → SUBMIT   │
                    └───────────────────────────┘
```

**Critical path:** catalogue → `:core` → J1 on device → J5 airplane → evidence → submit.
Voice (`:voice`) and the Office locator are parallel branches: valuable, but the submission must
never wait on them. **Android Studio/SDK download and the budget phone are on the critical path on
day 1 — start both today, not tomorrow.**
