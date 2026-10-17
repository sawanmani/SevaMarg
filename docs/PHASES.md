# PHASES — exit gates, not vibes

Six phases. A phase is **done** only when its gate checklist passes and the artefacts exist in
the repo. Red text = the gate that protects the 6 Nov irreversible submission.

Dates assume **today is 17.10.2026 (registration + PS lock deadline)**.

| Phase | Window | Name | Hard gate |
|---|---|---|---|
| 0 | **17.10 (today)** | Lock & register | Team ID + PS-locked screenshot in repo |
| 1 | 18–21.10 | Verified catalogue + **Android/voice spike** | APK runs on the budget phone, Vosk transcribes a real Hindi wav, validator green |
| 2 | 22–27.10 | Core journey, offline, accessible, bilingual | J1 + J4 + J5 pass in airplane mode on the cheap Android **in Hindi and English**, TalkBack clean |
| 3 | 28.10–01.11 | On-device voice + parity + locator | ≥85 % intent accuracy **on the phone**, HI/EN parity, ≤1.2 s e2e |
| 4 | 02–06.11 | Evidence, deck, video, **SUBMIT** | Synopsis uploaded 06.11 by 12:00 (not midnight) |
| 5 | 07–20.11 | Harden + stretch (lite APK, scale proof, PwD test) | Repo public, reproducibility script runs cold |
| 6 | 23.11–02.12 | Finale rehearsal + live defense | Demo runbook executed 3× clean, zero-network fallback proven, **judges' own phones installed** |

---

## Phase 0 — Lock & register (17.10, TODAY)

Non-negotiable sequence, ~45 minutes:

1. Register team at `ssaup.psweb.in/register` — category **College / University**, 2–5 members,
   institution + AISHE code (unknown → `X1111` is allowed by the form), district, leader OTP.
2. Challenge Type = **Better Public Services** → focus area **Digital public services**
   (the registration form's own track list; this is what maps to UPNIC-004).
3. Problem statement selection → **UPNIC-004** → confirm. **This is permanent.** Re-read the
   three scope bullets before clicking; the selection is a permanent DB commitment per the
   portal's own flow.
4. "Tell us about your idea" (300 char cap) — paste this (fits, verified length 283):

   > Seva-Marg: ek offline Android app jo Hindi awaz se batata hai - income, residence aur janm
   > praman patra ke liye kaun-se document, kaun-se office, kitni fees, kitne din aur aage kya
   > hoga. Har jawab sarkari source ke saath; pata na ho to guess nahi karta, office aur
   > helpline deta hai.

   (Longer variant for the synopsis/deck, not the form: add "Kisi bhi sadharan Android phone par
   bina internet chalta hai" — that pushes it over 300.)

5. Save Team ID + credentials (password = leader's 10-digit mobile) in `docs/proof/portal/`.

**Gate 0:** registration confirmation + PS-locked screenshot committed; Track + focus area
recorded in `README.md §Status`.
**If blocked by portal error today:** email the helpdesk the same day, screenshot the ticket,
and re-check whether the own-idea (Track B) fallback keeps the same timeline. Do not lose the
day; the catalogue work starts tomorrow regardless.

---

## Phase 1 — Verified catalogue + Android/voice spike (18–21.10)

Goal: the moat exists before any UI, **and the two riskiest native unknowns are killed by
measurement, not hope** (Vosk-Android Hindi accuracy, offline Hindi TTS).

- 1.1 **Android environment + spike** (M1 + M4, days 1–2, timeboxed): Android Studio + SDK 34,
  Gradle multi-module (`:app :core :voice :bench`), version catalog pinned, `:core` as pure
  Kotlin JVM with 2 throwing-away tests to prove the loop, Compose "Hello Seva-Marg" on the
  **budget phone via USB sideload**. Then the spike: Vosk-Android init + transcribe 5 real Hindi
  utterances; try Piper-via-sherpa-onnx and eSpeak NG for output; also note the English-model pair
  (Vosk small-EN + a Piper EN voice) against APK size, since the app is bilingual. Numbers →
  `results/voice-spike/`, decision → ADR-0003. **If the APK will not install on the budget phone
  by 20.10, that is the phase's red flag, not the catalogue's.**
- 1.2 Monorepo glue: `packages/catalogue` npm workspace, `build.mjs` emitting into
  `android/core/src/main/assets/`, GitHub Actions: `node.yml` (validate+parity) and `android.yml`
  (`:core:test`, `:app:assembleDebug`).
- 1.3 `service.schema.json` final + `validate.mjs` that **fails the build** if any field lacks
  `source_url` + `verified_on`, or if value == `VERIFY_OFFICIAL`.
- 1.4 Source hunt, 3 services × (eligibility, documents, fees, SLA, channels+steps, escalation,
  next-status meanings, FAQs). Official pages only: e-District UP, district `.nic.in` service
  pages, UP CSC / citizen charter. Screenshot each into `docs/proof/sources/`.
- 1.5 Fill `income-certificate.yaml` completely (gold service). Begin residence + birth.
- 1.6 `channel[].steps` baseline written **by hand from the official page** — this becomes the
  step-reduction denominator. Write it now, not after the UI, or the claim is retrofitted.
- 1.7 i18n keys + `parity.mjs`; catalogue `lang.hi/en` twin rule enforced.
- 1.8 Intent router v0 in `:core` (Kotlin): keyword+slot matcher over catalogue names/aliases,
  Devanagari + Hinglish transliteration normalisation, 15 unit tests via `./gradlew :core:test`.
  ADRs 0001 (no LLM on the answer path) and 0002 (native Android over PWA) are already written —
  review them against what you actually built and amend if reality diverged.

**Gate 1 (all must be true):**
- [ ] Debug APK installs and launches on the budget phone (Android Go / 2 GB RAM), screenshot in `docs/proof/`
- [ ] `results/voice-spike/`: Vosk Hindi WER + intent accuracy on 5 utterances, TTS engine chosen with reasons, APK size recorded
- [ ] `npm run validate:catalogue` green; **0** fields `VERIFY_OFFICIAL` for income-certificate
- [ ] Every cited URL has a matching screenshot file with date in filename
- [ ] Step baseline documented for ≥2 channels (online + CSC/tehsil)
- [ ] `./gradlew :core:test` green; CI runs validate + parity + `:core:test` + `assembleDebug`
- [ ] ADR-0003 **closed with spike numbers** (0001 and 0002 already accepted on 17.10)

**Cut-if-late:** birth certificate drops to Phase 3; 2 fully-cited services beat 3 half-cited.

---

## Phase 2 — Core journey, offline-first, accessible (22–27.10)

Goal: J1, J4, J5 working end-to-end with **text input only** (V0). Offline is a packaging fact,
not a caching promise — the catalogue ships inside the APK.

- 2.1 `:core` AnswerComposer: catalogue → typed `Answer` (documents/fee/office/SLA/next-steps),
  source line mandatory on every section; unit tests over 10 golden `Answer` JSONs.
- 2.2 `:app` Compose screens: Ask · Journey · Checklist · Office · About (5 routes, Material 3
  defaults). Answer region gets `Modifier.semantics { liveRegion = Polite }` so TalkBack reads
  the composed answer unprompted.
- 2.3 Refusal router in `:core`: confidence floor + staleness → "verified nahi" + nearest office
  + helpline. Build it **before** anything decorative; it is a demo moment (J4).
- 2.4 `:core` staleness + `district_overrides` merge, `kotlinx.serialization` tests on the real
  generated `catalogue.json` (catch schema drift at compile time, not on stage).
- 2.5 Room: session history only, **no PII columns in the schema at all**; `clearData()` wired to
  a visible button on About (a judge can verify deletion in one tap).
- 2.6 a11y pass: heading roles, reading order, ≥ 48 dp targets, contrast ≥ 4.5:1, Hindi ≥ 18 sp,
  D-pad focus; **`ComposeAccessibilityChecks` in `:app:androidTest` running on CI emulator**;
  manual TalkBack smoke on the real phone logged in `results/a11y/`.
- 2.7 Device matrix: budget Android (₹8–10k, 2 GB, Android Go) + one mid-range; record
  `adb shell am start -W` TotalTime, install time from APK, RAM headroom, whether airplane-mode
  J1 works. **No laptop in the matrix anymore — that is the point of the platform choice.**
- 2.8 Instrumented tests gating the phase: `J1HindiTextTest`, `J4RefusalTest`,
  `J5AirplaneModeColdStartTest` (the third sets airplane mode via `adb` and asserts zero network
  sockets opened by the process).
- 2.9 **Bilingual from day one (not a Phase-3 bolt-on):** Hindi is the default, English is always
  one tap away — a HI/EN toggle in the app bar, persisted in DataStore, applied to UI strings
  (`packages/i18n`) *and* answer content (catalogue `lang` twins) in the same action. Rules: no
  screen may exist in only one language; every number (fee, days, documents) must be **identical**
  in both; changing language mid-answer re-speaks/re-renders the same facts, not different ones.
  Hinglish/romanised input ("income certificate kaise bane") is accepted and answered in the
  **selected** output language. TalkBack must read the toggle and switch its speech language too.

**Gate 2:**
- [ ] `./gradlew :app:connectedAndroidTest` green on a physical device, including J5
- [ ] Accessibility checks: 0 errors on all 5 routes; TalkBack reads every answer card
- [ ] Cold offline start < 2 s (TotalTime) on the budget device, measured and logged
- [ ] Process opens **0 external sockets** during J1 (proof from `netcheck.py` in airplane mode)
- [ ] J4 refusal returns office+helpline for all 15 OOS evals (run through `:bench`)
- [ ] J1 + J4 completed **in both HI and EN** on the same device with identical facts; language
      choice survives app restart; `parity.mjs` green in CI

**Cut-if-late:** office map (replace with text "nearest CSC kaise dhoondhe" instructions);
analytics CSV export; the +size toggle (do the base contrast properly instead).

---

## Phase 3 — On-device voice, language parity, locator (28.10–01.11)

Goal: the 20-second "wow" — **Hindi speech in and out, entirely inside the phone**, with no
laptop and no network anywhere in the path.

- 3.1 `:voice` module: `AsrEngine` interface + `VoskAsr` implementation — model initialized on
  **first mic press** (not at launch) behind a progress state, streaming decode, mic released
  immediately after each utterance (a phone that holds the mic looks like a spy app to a judge).
- 3.2 `TtsEngine` interface + the engine chosen in ADR-0003 (Piper/sherpa-onnx, else eSpeak NG).
  Interrupt-on-next-press. `AndroidTts` only as an opportunistic extra when a Hindi engine exists.
- 3.3 Voice state machine in `:app`: idle → listening (haptic + waveform) → understood (show the
  transcript — users trust a visible transcription) → speaking. One long-press mic, available on
  every screen; `:core` never sees audio, only text.
- 3.4 20 recorded utterances (accent spread: city, Purvanchal, Awadh, Bhojpur) played into the
  **phone's own mic** → `asr_intent.py` + `:bench` → WER + intent accuracy in `results/asr/`.
  This is the number the deck quotes; no emulator audio, no speaker-perfected conditions.
- 3.5 Voice in both languages, decided by measurement not wishful thinking: the app's language
  selects the ASR/TTS model pair. Hindi voice is the **primary** path (`vosk-model-hi`). For
  English, the Phase 1 spike also checks the Vosk small-EN model + a Piper EN voice against APK
  size and init time — **if either language's voice cannot pass the spike, that language ships
  text + TalkBack and the deck says so plainly** (English text is fully complete either way;
  EN `questions_en.yaml` 50 items and the EN catalogue twins are *not* optional). `qa_accuracy.py`
  runs both languages → parity table.
- 3.6 Office locator: **pre-bundled OSM extract** per pilot district (`tools/osm/extract.py` →
  `core/assets/offices-<district>.json`) — address, hours, distance by haversine on the bundled
  coordinates, ODbL attribution on the Office screen. No live Overpass call in the demo path.
- 3.7 Voice-only mode toggle (visuals hidden, screen-off operable) + TalkBack verification of J1.
- 3.8 `refusal.py` over `questions_oos.yaml` **with voice input too** — ASR noise must not turn a
  refusal into a guess; garbled transcript must land in the refusal path, and we prove it.
- 3.9 `netcheck.py`: airplane-mode socket capture during a full voice J1 run → 0 external
  requests, committed as `results/asr/network-proof.txt`.

**Gate 3:**
- [ ] ≥ 85 % intent accuracy on the 20-utterance set, **on the budget phone**, with the failure list published
- [ ] End-to-end voice latency ≤ 1.2 s median on the budget phone (logcat timestamps)
- [ ] HI and EN answer accuracy both ≥ 95 % on the 50-question sets
- [ ] Refusal: 15/15 correct, **0 hallucinated facts** anywhere
- [ ] Voice-only J1 operable with screen off, one observer confirming steps aloud
- [ ] HI and EN both fully usable by **text** with identical facts; if EN voice ships, its intent
      accuracy is reported separately — if it does not, the limitation line is written in the deck
- [ ] 0 external requests during the whole voice run (proof file exists)

**Cut-if-late:** ship V0 text + V1 ASR with eSpeak NG output and say so; TTS polish is the first
thing to sacrifice, ASR is not. If Vosk accuracy is under 85 %, keep ASR in the build, demo with
the recorded utterances replayed to the phone, and **state that in the deck** rather than hide it.

---

## Phase 4 — Evidence, deck, video, SUBMIT (02–06.11)

The submission is an evidence package, not a pitch. Nothing here is creative work; it is the
harvest of Phases 1–3.

- 4.1 Field study: 25 walk-throughs in your ward/village with the instrument
  (`tools/field/instrument.csv`) — task done today without app vs with app; count steps,
  repeat-visit history, time-on-task, "kya aapko batana padega kisi aur ko". 1 CSC operator
  interview. Consent spoken + recorded, names not stored (no PII, consistent with §7 blueprint).
- 4.2 `stepcount.py` + `ingest.py` → the two charts that win the round: step delta, and
  drop-off/where citizens actually get stuck.
- 4.3 `scripts/verify-claims.sh`: every number in the deck regenerated from `results/` — run it
  on the final PDF text. If a number can't be regenerated, it comes out of the deck.
- 4.4 Synopsis package: structured technical abstract (`docs/SYNOPSIS_DRAFT.md`), pitch-deck PDF
  ≤ 20 MB (build guard), **public** demo video (unlisted YouTube is fine, must be reachable by
  evaluators 06.11–20.11).
- 4.5 Submit **06.11 before 12:00.** It cannot be edited afterwards. Buffer day exists so that a
  5 Nov failure is recoverable; a 6-Nov-23:00 failure is not.

**Gate 4 (submission gate):**
- [ ] Abstract, video URL, PDF all pass the portal's form validation and the PDF is < 20 MB
- [ ] Deck contains: promise table (3 scope bullets → 3 measured results), refusal benchmark,
      offline matrix, cost sheet, honest limitations, pilot ask
- [ ] Video shows J1(HI voice) → J2(EN) → J4(refusal) → J5(airplane mode) → step-delta chart
- [ ] `verify-claims.sh` output committed alongside the deck
- [ ] Repo README has licence/NOTICES + "how to reproduce in 5 commands"

---

## Phase 5 — Harden + stretch (07–20.11, during evaluation)

- 5.1 **Lite APK**: text-only variant (no model assets) for low-storage devices; publish both
  sizes with the tradeoff stated. Gate: `artifacts/*-lite-*.apk` < 20 MB and installs on the Go phone.
- 5.2 Analytics export: local Room query → CSV via SAF (no server, no PII, no uploader).
- 5.3 Scale proof: add a 4th service + one `district_overrides/` file in **under 30 minutes,
  zero code change**, and record the clock. This converts "scalable" from adjective to demo.
- 5.4 Public repo + docs + a 90-second repo tour video (evaluators may look; it costs nothing).
- 5.5 If reachable: usability test with 2–3 visually-impaired users via a school/NGO, with
  written consent, **on the real APK with TalkBack**. Report n and method, or don't report it.
- 5.6 Publish the APK to GitHub Releases with release notes + SHA256; print the QR for the booth.
- 5.7 Pilot ask drafted: one-page letter to IT & Electronics / UP e-Gov (CSC) proposing a ward
  pilot with the APK + a 10-phone loan plan — departments move on proposals that arrive pre-written.

---

## Phase 6 — Finale (23.11–02.12)

- 6.1 Demo runbook: exact device, exact taps, exact phrases, exact fallback ladder
  (live voice → pre-recorded utterance → text mode → backup video). Rehearse 3× clean.
- 6.2 Venue paranoia: no Wi-Fi assumed (irrelevant now — the app needs none), no projector
  assumed, both test phones at 100 % + power bank, APK pre-installed and verified the night
  before, models already inside the app (nothing downloads on the day), `adb` cable for capture.
- 6.3 Q&A bank from `docs/EVIDENCE_AND_DEMO.md §7`, answered aloud by the *non*-author of each
  part, so answers survive follow-ups.
- 6.4 Two people must be able to run the demo alone (one gets sick / one speaks to judges).
- 6.5 Printed: deck, promise table, benchmark table, source-screenshot appendix.

**Gate 6:** a full 8-minute slot executed on zero network, twice, with no restart.

---

## §12 Cut list (in the order things die when time runs out)

1. Analytics CSV export
2. Office map / bundled OSM extract (text instructions "nearest CSC kaise dhoondhe" are fine)
3. Birth certificate service
4. Voice-only mode toggle (keep voice in/out on the normal screen)
5. English parity *set* (keep EN content, shrink eval to 20)
6. Second district's `district_overrides` (keep one, name the second as designed-not-built)
7. Piper → accept eSpeak NG (robotic offline voice still passes the statement)
8. Lite APK (the full bundle is what we demo)

**Never cut:** the income-certificate catalogue with citations, the refusal path, the
airplane-mode J1 test **on a physical phone**, the step-count benchmark, and honest limitations in
the deck. Those are the entry — and on Android the airplane-mode test is the easiest of the five
to keep true, because there is no network path to accidentally fall back on.
