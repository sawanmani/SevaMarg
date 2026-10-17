# EVIDENCE & DEMO — how this entry is proved and performed

Two halves: the **evidence factory** (numbers the deck may contain) and the **performance**
(video + live defense). If a claim has no row here, it does not go in the deck.

---

## 1. Benchmark protocols

### 1.1 Step-reduction (scope bullet 1)

- Baseline is taken from the **official channel steps** recorded in the catalogue
  (`channel[].steps`, hand-counted from the source page in Phase 1.5) — not from opinion.
- App-side steps counted by `stepcount.py` driving the real UI via `adb`/uiautomator (the tap
  sequence per task: *learn requirements → learn fee → learn office → know timeline → know next
  step*), mirrored by an Espresso test that asserts the same screen count in CI.
- Report as: `median steps offline-channel (nscanned) → steps in Seva-Marg`, per task, plus
  **visits avoided** (a visit is a stronger unit than a tap).
- Output: `results/stepcount/stepcount.json` + one bar chart. State both numbers, not the %.

### 1.2 Language parity (scope bullet 2)

- `evals/questions_hi.yaml` (50) and `questions_en.yaml` (50), each with an expected-fields
  answer key (documents set, fee value, office type, SLA days).
- `qa_accuracy.py` scores **exact field match**, separately per language; parity = |HI − EN| ≤ 2 pp.
- Also assert structural parity: same cards rendered, same source line, `parity.mjs` on the
  catalogue `lang` twins so an EN field can't silently be missing.

### 1.3 Refusal / anti-hallucination (our differentiator)

- `questions_oos.yaml`: 15 out-of-scope or ambiguous (caste certificate, death certificate,
  "Mere naam ka record kahan hai", expired-scheme query, district with a different fee).
- Pass = 15/15 route to refusal (office + helpline + "verified nahi"), **0 invented facts**.
- Run twice: text input, and through ASR (mis-transcription must not create a false answer).

### 1.4 Basic-smartphone usability (scope bullet 3 — the row Android wins on)

- `results/device-matrix/` rows: device, Android version, RAM, **APK variant (full / lite) + size**,
  install method (sideload/QR), install time, cold start s (`am start -W` TotalTime), memory
  headroom, TalkBack works (Y/N), on-device voice usable (Y/N), storage left after install.
- The **budget device is the headline row**. Testing only on your own flagship does not meet the
  scope bullet.
- Offline matrix: airplane-mode J1, app swiped away from recents, airplane mode + never opened
  before (fresh install → first launch → full Hindi voice journey). With a bundled asset there is
  **no "first-run fetch" caveat to explain** — say that out loud, it is a real difference from a PWA.
- Bonus row: `0 external sockets observed during the whole run` (proof from `netcheck.py`).

### 1.5 Voice quality

- 20 recorded utterances (4 accent groups × 5), played into **the phone's own mic**, on the budget
  device. Report WER **and** intent accuracy separately; intent accuracy is the number we win on.
  Report the failure list honestly.
- End-to-end latency median + p95 (capture → on-device decode → intent → compose → first audio),
  from logcat timestamps. **There is no LAN tier anymore; if a number needs a laptop, it does not
  go in this table.**

### 1.6 Field study (the human number)

- n = 25 walk-throughs, mixed: first-time applicants, one group senior citizens, one PwD if reachable.
- Instrument (per respondent, no names): task attempted, steps/visits today, time on task,
  did they need another person's help, did they use an agent and what did it cost, then the same
  task with the app; SUS optional — do **not** add a validated-scale ritual you can't administer properly.
- Report n, method, and what you did **not** control for. Never write "users found it easy" —
  write "18/25 completed the task unaided, in a median 41 s".

---

## 2. Anti-claim guardrail

`scripts/verify-claims.sh` extracts every numeral from the deck text, re-runs the matching
benchmark and diffs. Run it on the final PDF before upload. This is the cheapest way to make
the defense unassailable: any judge probe lands on a reproducible artefact.

---

## 3. Deck skeleton (11 slides, PDF ≤ 20 MB, one-shot — no edit after submit)

| # | Slide | Content |
|---|---|---|
| 1 | Title | सेवा-मार्ग · Seva-Marg · UPNIC-004 · team, institution, district |
| 2 | The problem, in one citizen's words | from your own field notes, not a generic para |
| 3 | **Promise table** | the 3 scope bullets verbatim → 3 measured results (numbers on slide 3, not slide 9) |
| 4 | Product | J1 screenshot strip: ask → answer → checklist → next step, HI and EN side by side |
| 5 | Why the answers can be trusted | citation-per-answer, staleness banner, **refusal benchmark 15/15, 0 hallucination** |
| 6 | Offline + basic phone | device matrix (2 phones + APK sizes), airplane-mode J1 result, **0-external-requests proof**; architecture diagram |
| 7 | Accessibility | TalkBack/voice-only path; senior-citizen use; "works screen-off" claim + its recorded test |
| 8 | Step reduction | the step-delta chart + visits avoided + where citizens drop off |
| 9 | Open source & cost | stack table with licences (incl. the eSpeak/GPL or Piper decision); ₹0 per citizen cloud cost; **Vosk + ServicePlus precedent**; repo + APK release link |
| 10 | Pilot ask | what we want from IT & Electronics: 1-block pilot with 10 pre-loaded phones, catalogue co-verification, feed into state portal |
| 11 | Limitations + next | honest list (3 services, one district, ASR accuracy number, eSpeak-grade voice if that was the pick, no submission/payment) |

Design rules: numbers over adjectives, Hindi text rendered at ≥18 sp in screenshots (device
screenshots, not mockups), every figure traceable to `results/`, no stock imagery, no
"AI-powered" as a bullet.

---

## 4. Synopsis abstract (structure for `docs/SYNOPSIS_DRAFT.md`)

1. **Problem & user** — 4 lines, one measured fact from the field study.
2. **Approach** — deterministic cited catalogue → voice-first navigator; no LLM on the answer
   path; refusal path; offline bundle. (The architecture *is* the novelty claim.)
3. **Technology** — Kotlin + Jetpack Compose Android app; `:core` pure-JVM deterministic engine;
   catalogue compiled into the APK asset; on-device Vosk Hindi ASR + offline TTS (Piper/eSpeak-NG);
   Room (no PII), WorkManager staleness check; OSM extract bundled; AI4Bharat IndicTrans2 for
   human-reviewed content prep only. Open source + licences checked.
4. **Validation** — step delta, HI/EN parity ≥95 %, refusal 15/15, airplane-mode device matrix,
   n=25 field study. Method stated in one sentence each.
5. **Impact & deployability** — 3 services today, catalogue-only scaling, ₹0 marginal cost,
   pilot request, fit with the state's existing service portals.
6. **Limitations** — 3 honest lines.
7. **Demo video link** + repo link.

---

## 5. Demo video script (≤3 min, recorded 05.11, public/unlisted)

| s | Shot | Point |
|---|---|---|
| 0–15 | District street/CSC queue, then a phone in hand | "Agent ke paas jaana padta tha" — one field quote |
| 15–45 | Airplane mode ON, app opened cold | claim: no internet, no laptop dependency for the core journey |
| 45–95 | J1 spoken Hindi → answer cards → source line zoomed | tap the source line; show the citation |
| 95–120 | Toggle EN, same question | parity, same numbers |
| 120–140 | J4 out-of-scope question → refusal + office + helpline | "We chose not to guess" — the trust moment |
| 140–160 | TalkBack on, phone face down, J1 completed by voice | accessibility, screen-off |
| 160–175 | **A second person (not the team) installs from the QR on their own phone and asks one Hindi question** | "it runs on a normal phone, we don't own the demo" — the strongest 15 seconds |
| 175–180 | Benchmarks on screen: step delta, accuracy, refusal, device matrix, 0-network proof | numbers, no voiceover hype |
| 180 | Team + repo URL + APK release + one-line pilot ask | end |

Rules: no cuts inside a demo segment (proves it's real), on-screen timestamp, record with the
**airplane indicator visible and no laptop in frame**, keep a raw unedited take on disk. Screen
mirror the phone via `adb` capture (not a photo of a screen) so Hindi text is readable at 1080p.

---

## 6. Live demo runbook (finale, 02.12) — fallback ladder

| Tier | State | If it fails |
|---|---|---|
| L0 | Live Hindi voice, on-device, airplane mode, budget phone in hand | → L1 |
| L1 | Pre-recorded utterance played near the phone's mic (same app, same path, no network) | → L2 |
| L2 | Text input + chips in the same app (all benchmarks identical) | → L3 |
| L3 | Backup video already public from the synopsis, narrated live, phone still in hand | → L4 |
| L4 | **Judge's own phone** running the release APK (installed earlier in the day) | narrate their taps |

Pre-show checklist (T-30 min): both test phones at 100 % and **already installed + opened once**
(model initialized), release APK + QR printed, catalogue asset version shown on About, airplane
mode confirmed working on both, `adb` cable for screen capture, deck PDF + printed promise table,
one teammate holding the spare phone ready to swap in.

**Never** do this at the venue: install an APK live in front of judges as your *only* path (do it
before, offer it as a bonus), download a model, depend on venue Wi-Fi, or let a teammate's
flagship be the only device that has ever run the app.

---

## 7. Q&A bank (predicted attacks + our answers)

| Attack | Answer |
|---|---|
| "Ye to FAQ website hai." | The deliverable is a *verified service catalogue + step reduction*, measured: [step delta], [visits avoided], parity ≥95 %, and the same catalogue scales by adding YAML, not code — we scaled it live in 27 minutes. |
| "Fee galat ho gayi to?" | It can't be silently wrong: every field has source + verified-on date, staleness triggers a banner and the refusal path, and no model writes the answer. Here is the validator that fails the build on an uncited field. |
| "Offline ka matlab kya?" | Airplane mode, this phone, right now — the whole journey works, voice included, because ASR and TTS run on the device and the data is inside the APK. Here is the socket-capture log showing zero external requests. |
| "PWA / website kyun nahi banaya?" (or "React native / Flutter kyun nahi?") | Because a browser cannot do offline Hindi speech recognition — Web Speech calls Google's servers — so a web entry needs either no voice or a laptop on the LAN. We chose the platform where the claim is literally true. Kotlin ramp-up cost was accepted and is why our logic lives in a pure-JVM module. |
| "Aap government data ko kaise verify karoge, continuously?" | Ward-level volunteer + department co-verification, staleness window, and a "report a change" path that exports a diff for the department. We don't claim auto-correctness. |
| "Accessibility sirf add-on to hai?" | TalkBack/voice-only is on the critical path — J1 completes with the screen off, and here is the recorded test. |
| "Doosre states/citizen?" | Catalogue is per-state data; district overrides already exist as a pattern. No code change. |
| "Team size / kaunse part kaun banata hai?" | Named owners for catalogue, UI, content/field, voice/bench — each answers for their own numbers. |
| "Privacy?" | No accounts, no name/phone fields in the schema at all; Room data is local and there is a visible "clear my data" button on About; no analytics SDK, no crash uploader, no background sync of user data — the only scheduled work is an optional content-staleness check. |
| "Play Store par kyun nahi?" | Timeline and ownership: this is a prototype for a department pilot, so we distribute a debug-signed APK with a published hash and source. The pilot ask is exactly for the channel where a signed store release makes sense. |
| "Microphone permission — yeh data bhej to nahi rahi?" | `RECORD_AUDIO` is the only permission we ever **ask** the user for. `INTERNET` is in the manifest solely for an optional staleness ping; deny it or use airplane mode and the whole journey still completes, because the catalogue and models are inside the APK — and `netcheck.py` shows zero sockets. Here is the manifest, three lines. |

---

## 8. What we will say about limitations (say it first, before they find it)

Three services, not twenty · one district's field study (n=25), not state-representative · the
on-device TTS is [engine] and sounds synthetic — we chose guaranteed offline over a natural voice
that needs a network · content staleness is managed by a 180-day window plus department
co-verification, not live portal sync · no application submission or payment — deliberately: we
are the clarity layer *before* the portal, not a parallel portal · debug-signed APK only, no store
release yet.

Judges at state-level panels rarely reject for limitations. They reject for *uncited claims and
a demo that only works on the developer's phone with Wi-Fi*. Both are structurally handled above.
