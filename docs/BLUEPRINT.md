# BLUEPRINT — Seva-Marg (UPNIC-004)

## 1. Product definition

**One-liner:** An offline-first, voice-first, TalkBack-first **Android app** that tells a UP
citizen exactly *which documents, which office, what fee, how many days, and what happens next*
for a government certificate — in their own language, with the source shown, with Hindi speech
recognition running **inside the phone**.

Platform is a product decision, not a preference: a browser cannot do real offline Hindi ASR,
so a PWA would have needed a nearby laptop for voice. Native removes that dependency — see §5.

**Prime beneficiary (pick one, stay narrow):** a first-time applicant for an **income
certificate** in a semi-urban/rural ward who has never used e-District and normally pays an
agent. Secondary: the same person's parent (senior citizen, low vision) — this is where the
accessibility work earns its keep.

**Explicitly NOT the user:** an educated urban user who already knows the portal, and a
government operator processing the application. We are not building the issuance system.

### Why this wins a state-level expert panel

1. The statement's three scope bullets are **literally testable acceptance criteria**. Most
   competing entries will present a "governance app" with no measurable claim; we present a table.
2. The sponsoring department (IT & Electronics) is the same department that owns the abhiyaan's
   **state portal** work per the official Karyayojana. Our pitch is an *extension of their
   roadmap*, not a new burden — that framing is what converts a win into a pilot.
3. Every answer is citation-backed, so the single most common failure mode of AI governance
   entries — hallucinated fees/documents — is structurally impossible in our demo path.

---

## 2. Scope fence

### In scope (v1, ships by 06.11)

| Service | Why chosen |
|---|---|
| **Income certificate** (आय प्रमाण पत्र) | Highest volume, most agent-mediated, official fee/SLA published on e-District UP |
| **Residence / domicile certificate** (निवास प्रमाण पत्र) | Confusing eligibility variants (permanent/temporary) → strong "clarity" story |
| **Birth certificate** (जन्म प्रमाण पत्र) | Late-registration edge cases make "what happens next" genuinely valuable |

Three services × complete, verified, both languages > twenty services × shallow. Judges ask one
question; we must answer it perfectly and cite it.

### Out of scope (write "not built, deliberately" in the deck — a fence reads as maturity)

Application submission/payment integration, any authenticated portal scraping, issuance
workflow, Aadhaar/DPIIT anything, multi-state, blockchain, mobile-number OTP by us, PwD
sign-language translation, doctor/agent marketplace, iOS, **Play Store listing** (we sideload
an APK — a store review cycle inside this timeline is a self-inflicted deadline), **Web/PWA
version** (dropped: it cannot deliver offline voice, see §5).

---

## 3. Golden journeys (5 — these are the demo and the test suite)

| # | Journey | Trigger | Success = |
|---|---|---|---|
| J1 | "मुझे आय प्रमाण पत्र बनवाना है" | spoken HI | documents + fee + office + SLA + next-steps, all spoken + shown, source line visible |
| J2 | "Which documents do I need for an income certificate?" | spoken EN | identical content, parity-checked against J1 |
| J3 | Form-field explainer | user opens the field checklist | each field read aloud in simple Hindi with an example value; TalkBack-only operable (screen off) |
| J4 | "क्या यह सही है?" → unknown/ambiguous question | e.g. caste certificate | **refusal path**: "ये सेवा मैं verified नहीं कर सकती" + nearest office + helpline | 
| J5 | Airplane-mode cold start | Wi-Fi + mobile data off, app swiped away from recents | J1 completes end-to-end, latency unchanged, **no laptop anywhere** |

J4 and J5 are the two that differentiate. Almost nobody demos a refusal path or a real cold
offline start on stage.

---

## 4. Architecture

```
                     ┌──────────────────────────────────────────┐
                     │   packages/catalogue   (source of truth)  │
                     │  YAML per service; every fact field has   │
                     │  { lang{hi,en}, source_url, verified_on } │
                     └───────────────┬──────────────────────────┘
                                     │ npm run catalogue:build  (validate + compile)
                                     ▼
            android/core/src/main/assets/catalogue.json   ← shipped INSIDE the APK
                                     │
   ┌─────────────────────────────────▼────────────────────────────────┐
   │  ONE ANDROID APP — no server, no WebView, no CDN                 │
   │                                                                  │
   │  :app    Jetpack Compose UI                                      │
   │          routes: Ask · Journey(J1–J5) · Checklist · Office ·     │
   │          About ;  TalkBack semantics; voice-only mode; big-text  │
   │          ViewModel + StateFlow; minSdk 26 (Android 8) so low-end │
   │          Go devices are in scope, not an afterthought            │
   │    │                                                             │
   │    ├─ :core   pure Kotlin JVM module — zero Android deps         │
   │    │     • intent router   : deterministic keyword + slot match  │
   │    │     • answer composer: catalogue fields → cards + sentence  │
   │    │     • refusal router : confidence < τ or stale → office +   │
   │    │       helpline (never a guess)                              │
   │    │     ⇒ the same module runs the eval sets as a Gradle test,  │
   │    │        so benchmarks need no device, no emulator, no network│
   │    │                                                             │
   │    └─ :voice   Vosk-Android offline Hindi ASR + TTS engine iface │
   │               (sherpa-onnx/Piper → eSpeak NG fallback → optional │
   │                Android TextToSpeech when the engine is present)  │
   │                                                                  │
   │   Room: local history only, no PII columns in the schema at all  │
   │   WorkManager: optional staleness ping — never needed to answer  │
   │   assets/: catalogue.json + vosk-model-hi + tts voice            │
   └───────────────┬──────────────────────────┬───────────────────────┘
                   ▲                          ▲
     debug-signed APK + QR code        :bench (Kotlin) + tools/ (Python)
     on the finale table →             WER, qa accuracy, refusal, field
     judges install on their OWN       ingest, deck charts, verify-claims
     phones                            ← the evidence factory
```

**Key architectural decisions and why**

- **Native Android, because of one capability.** Real offline Hindi speech recognition is not
  available to a browser (Web Speech API calls Google's servers). The honest PWA design needed
  a laptop on the same LAN to do ASR. Native kills that dependency — the phone alone does
  everything, which is exactly what "usable on a basic smartphone" asks for. Cost: Kotlin ramp-
  up, which is why `:core` is plain Kotlin JVM and testable without a device (Phase 1 spike).
- **Deterministic intent router, not an LLM, on the answer path.** Catalogue lookup + slot
  filling. Any LLM use is confined to *offline paraphrase generation* whose output a human
  reviews and then freezes into the catalogue. Reason: a judge's first attack on a governance
  AI entry is "what if it states a wrong fee?" Our answer: it cannot — the model never writes
  the answer, and we can show the guardrail in code.
- **Confidence + staleness.** Every field has `verified_on`. If older than `stale_after_days`, the answer is prefixed with "यह जानकारी <date> तक सत्यापित है" and the refusal router offers
  the office number. Cheap to build, huge credibility in defense.
- **Catalogue is data, not code.** YAML → JSON Schema → `assets/catalogue.json`. District
  variance is a `district_overrides` map, so "scalable across UP" is a data claim, provable:
  a 4th service = 1 YAML file, 0 Kotlin changes. We will demo that count and time it.
- **Model assets load on first mic press, not at launch.** The app opens instantly (text path
  works immediately); Vosk's ~40–80 MB model initializes behind a progress state. This is how
  a 2 GB RAM phone gets a < 2 s cold start *and* offline voice.
- **Analytics is an export, not a backend.** A local Room query written to CSV via SAF — the
  department sees *where* citizens drop off without us running a server or collecting data.
  No login system. No telemetry. Nothing leaves the phone.

---

## 5. Voice model: on-device by default (and the honesty that goes with it)

| Tier | Path | Offline? | Status |
|---|---|---|---|
| V0 | Text input + quick-reply chips ("दस्तावेज़", "फीस", "कितने दिन") | ✅ always | Phase 2 — baseline, never removed |
| V1 | **Vosk-Android on-device Hindi ASR** (streaming, mic → text in-process) | ✅ | Phase 3 — **primary voice path** |
| V2 | On-device TTS: Piper voice via **sherpa-onnx** Android, fallback **eSpeak NG** (Hindi phonemizer) | ✅ | Phase 3 — engine picked by the spike in §5.1 |
| V3 | Android `TextToSpeech` when a Hindi engine is already installed | ⚠️ engine-dependent, may need a download | optional bonus, never the demo path |

**What we say to the judges, exactly:** "Voice runs inside the phone. There is no cloud call
anywhere in the answer path, and no laptop in the demo."
Do **not** say "we used the browser speech API" and do not demo with an emulated mic — one
airplane-mode, one real mic, on a real phone.

### 5.1 TTS/ASR engine spike (Phase 1, 3 days, decided by measurement)

Three questions, answered with numbers in `results/voice-spike/`:
1. Does `vosk-model-hi` give usable Hindi WER on our 20 real utterances at ≥ 1 CPU thread on a
   2 GB RAM device? Target intent accuracy ≥ 85 %.
2. Which offline TTS actually speaks *Hindi* acceptably on-device: Piper voice (natural, bigger,
   JNI/AAR integration risk) vs eSpeak NG (robotic, tiny, zero risk)? Decide by listening test
   with 5 people + integration time cost. Ship the one that works on the budget phone.
3. Total APK size and first-mic-press init time at each option. Report it honestly in the deck
   (a 120 MB APK with a stated reason beats a 15 MB APK that cannot speak Hindi).

If sherpa-onnx or Piper integration slips past 01.11: ship **V0 + V1(ASR) + eSpeak NG**, and
state it as a limitation. A robotic voice that works offline is far better than a natural voice
that needs a laptop or a network.

**Latency budget (V1+V2, measured on the budget device):** mic capture → streaming decode
≤ 600 ms → intent match ≤ 10 ms → compose ≤ 20 ms → TTS first audio ≤ 450 ms. Target
**end-to-end ≤ 1.2 s median**, p95 logged, results committed.

**Install story (this is a competitive advantage, use it):** debug-signed APK on GitHub Releases
+ a QR code printed on the finale table. Any judge can install it on their own phone in ~90
seconds and speak Hindi at it. A PWA entry cannot offer that on a low-end handset, and a web
demo cannot survive a dead venue Wi-Fi.

---

## 6. Data model (catalogue contract)

```yaml
service_id: income-certificate
state: UP
district_scope: state                 # or district: <code>
lang:
  hi: { name: "आय प्रमाण पत्र" }
  en: { name: "Income Certificate" }
eligibility: [ { text: {hi,en}, source_url, verified_on } ]
documents:   [ { item: {hi,en}, who: "applicant|guardian", copies: 1, attestation: bool,
                        source_url, verified_on } ]
fees:        [ { item: {hi,en}, amount_inr: number, mode: "online|cash", source_url, verified_on } ]
sla:         { days: number, scope: "from application to delivery", source_url, verified_on }
channel:     [ { kind: "online|csc|tehsil", steps: [ { n, action:{hi,en}, needs_visit: bool } ] } ]
office:      { type: "tehsil|csc|dm_office", how_to_find: nearest_csc_query }
escalation:  [ { when: {hi,en}, do: {hi,en}, contact_source } ]
next_steps:  [ { status: "applied|verified|approved|delivered", means: {hi,en}, eta_days } ]
faqs:        [ { q:{hi,en}, a:{hi,en}, source_url, verified_on } ]
stale_after_days: 180
confidence_floor: 0.85
```

`channel[].steps` is the **step-reduction measurement backbone** — the baseline is literally
counted from the official channel steps, and our app's steps are counted in the UI. Same unit
on both sides, so the "N → M" claim survives scrutiny.

**How the app consumes it:** the Node build emits `android/core/src/main/assets/catalogue.json`;
`:core` deserializes it with `kotlinx.serialization` into the same shape (data classes in
`:core/src/main/java/.../catalogue/`). One schema, two readers, zero drift — and because the
asset lives inside the APK, "offline" is a packaging fact rather than a caching promise.

---

## 7. Safety, privacy, legal guardrails (say these out loud in the deck)

- **Not a government platform.** Persistent footer disclaimer in hi/en:
  "यह एक शैक्षणिक प्रोटोटाइप है, सरकारी पोर्टल नहीं। आधिकारिक जानकारी के लिए e-District/CSC देखें।"
- **No personal data collected.** No signup, no phone number, no name field. Local-only
  storage; clearing app data wipes everything. Privacy is therefore a *structural* claim, not a
  policy promise — ideal for a health/citizen-adjacent data defense question.
- **Public pages only.** Catalogue built by reading published citizen-charter/service pages;
  no authenticated portal access, no scraping of login-gated systems, source URLs recorded.
- **Never advise on eligibility decisions**; we explain requirements, the office decides.
- **Distribution without a store:** debug-signed APK, hash published in `docs/proof/`, source
  in the repo. No Play Store listing (a review cycle inside this timeline is a self-inflicted
  deadline).
- **Permissions: microphone + internet, and that is all.** `RECORD_AUDIO` (runtime, for voice
  input), `INTERNET` + `ACCESS_NETWORK_STATE` used **only** by the optional WorkManager staleness
  ping — deny them or fly in airplane mode and every journey still completes, because the
  catalogue and models are in the APK. **No location permission** (the user picks the district),
  no contacts, no phone state, no camera.
- **No background sync of user data, no ads, no analytics SDK, no crash reporting uploader.** If a
  judge runs a network monitor during the demo, nothing should leave the phone. That is a testable
  claim, so we will test it (`netcheck.py`) and put the log in `results/`.
- **Licences:** every vendored model/lib gets an entry in `NOTICES.md` with SPDX id and the
  model-weight licence terms checked separately from the code licence (AI4Bharat-style open
  weights must be re-read for attribution/commercial clauses before we call it "open source"
  on a slide).

---

## 8. Accessibility design (this is where the assistive-tech story legitimately lands)

- **Compose semantics first**, not visual polish: every answer card is a labelled
  `Modifier.semantics { }` node with `Role` and a `liveRegion = LiveRegionMode.Polite`, so a
  TalkBack user hears the composed answer without re-tapping. **Nothing is visual-only** — a
  value that exists only as colour or icon fails review in our own PR checklist.
- Reading order, headings (`Role.Heading` on section titles), touch targets ≥ 48 dp, contrast
  ≥ 4.5:1, Hindi body text ≥ 18 sp with a one-tap +size control, focus indicator visible in
  D-pad/keyboard navigation.
- **Voice-only mode:** a single toggle that hides visuals and drives J1–J4 by speech alone.
  Demoable with the phone face down — the strongest 20 seconds of the finale. Long-press mic
  anywhere on screen (no hidden gesture), haptic confirm on listening/understood.
- **Automated accessibility gates in CI, on an emulator and one real device:**
  `ComposeAccessibilityChecks` (Espresso `checkAccessibility`), an `android-lint` a11y rule set,
  plus a recorded TalkBack walkthrough log in `results/a11y/`. axe-core is gone with the browser.
- Language simplicity: answers in ~grade-8 Hindi, no bureaucratic compounds; the paraphrase
  pipeline (human-reviewed) exists specifically for this.

We are **not** claiming blindness-device innovation — we are claiming that a citizen-service
app that works for a low-vision senior citizen is a governance win, which is exactly the
statement's "clarity of access" wording.

---

## 9. Metrics we will actually report

| Metric | Instrument | Baseline | Target |
|---|---|---|---|
| Steps in journey | `tools/bench/stepcount.py` over catalogue `channel.steps` vs app | official channel steps | −50 % |
| Repeat visits avoided | field sheet Q ("kitni baar jaana pada") n≥25 | measured in field | reported as observed |
| Answer accuracy (HI / EN) | 50+50 eval set, exact-field match | — | ≥ 95 % / ≥ 95 % |
| Refusal correctness | 15 out-of-scope questions | — | 100 % refuse, 0 hallucination |
| ASR intent accuracy | 20-utterance recorded set, real accents, played into the phone's mic | — | ≥ 85 % (V1, on-device) |
| Cold offline start (airplane mode) | `adb shell am start -W` TotalTime, 5 runs on the test phone | — | < 2 s |
| First voice response latency | logcat timestamps, median + p95 over 20 runs | — | ≤ 1.2 s median |
| Install on a low-end device | manual install on 2 devices incl. Android Go | — | works, APK size reported |
| Network calls during a full J1 run | `tcpdump`/Proxyman capture on the device | — | **0 external requests** |
| Catalogue coverage | validator: % fields with `source_url`+`verified_on` | — | 100 % |
| Cost to run | cloud cost per citizen | — | ₹0 (everything ships in the APK) |

Report the number of participants and what was *not* tested. Honest limitations beat a
polished claim that unravels in the viva.

---

## 10. Stack and open-source anchors

| Layer | Choice | Licence posture |
|---|---|---|
| App | **Kotlin + Jetpack Compose**, Material 3, minSdk 26, targetSdk 34 | Apache-2.0 |
| Architecture | MVVM, single Activity; `:app` / `:core` (pure JVM) / `:voice` / `:bench` Gradle modules | — |
| Data in app | `kotlinx.serialization` for the catalogue asset; **Room** for local history; DataStore for settings | Apache-2.0 |
| Background | **WorkManager** staleness check (only when a network exists; never required to answer) | Apache-2.0 |
| ASR (offline, on-device) | **Vosk Android** (`vosk-model-hi-*`, ~40–80 MB) — `org.vosk-android` | Apache-2.0 (verify model-weight licence) |
| TTS (offline, on-device) | **Piper voice via sherpa-onnx** Android, fallback **eSpeak NG** (Hindi phonemizer) | BSD-3 (sherpa-onnx) / GPL-3 (eSpeak NG → no code linking, use as a separate binary/engine or pick Piper; check in spike) |
| Optional TTS | Android `TextToSpeech` framework when a Hindi engine is installed | platform |
| Dev tooling | Android Studio / Gradle, `adb`, Espresso,paparazzi snapshot tests | Apache-2.0 |
| Translation for catalogue prep | **AI4Bharat IndicTrans2** open weights — offline content prep only, human-reviewed | check weight licence |
| Office location | **pre-bundled static OSM extract** per pilot district (no live Overpass call at demo time) | ODbL — attribution on the Office screen |
| Gov-service analogue | **ServicePlus / Service Online** (eGov Foundation) as open-source precedent for a citizen-service layer | verify repo licence |
| Bench + field pipeline | Python (pandas, jinja reports) + Gradle `:bench` task for the Kotlin eval runner | MIT/BSD |
| Diagrams/deck | Mermaid → PDF export | — |

Note on **eSpeak NG being GPL-3**: if we ship it, we either invoke it as a separate process/
asset (no linking) or we accept the obligation to publish that modification. Decide inside the
Phase 1 spike and record it in `NOTICES.md` — a licence we cannot explain is a licence we should
not ship.

Two OSS names doing real work in the narrative: **Vosk** (proves offline Hindi ASR is
commodity, so our claim is architecture not magic) and **ServicePlus** (proves a citizen
service layer is a national open-source pattern — we are extending it for UP citizens, not
inventing). Verify both licences before they hit a slide.

---

## 11. Risk register (top 8, Android-first)

| Risk | L | Impact | Mitigation |
|---|---|---|---|
| Official values change / wrong page cited | M | Fatal | `verified_on` + staleness banner + refusal router; screenshot each source into `docs/proof/sources/` |
| **Kotlin/Compose ramp-up eats Week 1** | **H** | High | `:core` is plain Kotlin JVM, so the hardest logic is written and unit-tested without an emulator; UI stays 5 routes with Material 3 defaults (no design system); M1 pairs with M2 on the first three screens |
| **Vosk-Android Hindi accuracy poor on real accents** | M | High | Phase 1 spike measures it on 20 recorded utterances *before* anything else is built; if intent accuracy < 85 %, we ship V0 text + honest WER report and say so in the defense |
| **Offline Hindi TTS slips (Piper/sherpa-onnx AAR integration)** | M | Medium | eSpeak-NG fallback chosen inside the same spike; a robotic offline voice still satisfies the statement; **never** fall back to cloud TTS |
| APK too large for a low-storage / Android Go phone | M | Medium | Two artifacts: full voice bundle (demo + field) and a text-only lite APK; publish both sizes *with the reason* on the slide |
| Sideload/USB debugging fails on finale day | M | Medium | Two test phones pre-installed and checked the night before; QR + APK mirrored on a second channel; `adb` cable and a power bank in the demo kit |
| "Aapne to bas FAQ app banaya" objection | M | High | Step-count delta + refusal benchmark + on-device Hindi voice + **the judge's own phone running it**; it is a service, not a page |
| Team bandwidth (3 weeks, college semester) | H | High | Phase gates with explicit cut list; J1+J5 are the minimum viable demo; analytics export and the 2 extra districts drop first |
| Synopsis irreversible + submitted early with weak numbers | M | High | Freeze evidence by 03.11, deck on 04.11, video on 05.11, submit 06.11 morning not midnight |
