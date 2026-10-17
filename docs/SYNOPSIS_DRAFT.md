# SYNOPSIS DRAFT — UPNIC-004 · सेवा-मार्ग / Seva-Marg

> Written for the portal's synopsis form (abstract + public demo video URL + PDF ≤ 20 MB).
> **Submitted 06.11.2026 before 12:00 — cannot be edited afterwards.**
> Every `⟨…⟩` is filled mechanically from `results/` on 04.11. If a placeholder has no
> number behind it on 04.11, the sentence gets deleted, not softened.

## Metadata block

| Field | Value |
|---|---|
| Problem statement | UPNIC-004 — Digital Governance for Citizen Services |
| Department | IT & Electronics Department |
| Challenge Type (form dropdown) | Better Public Services → Digital public services |
| Team category | College / University |
| Platform | Android (Kotlin + Jetpack Compose), minSdk 26; debug-signed APK distributed by QR/Release — no Play Store, no server |
| Institution / District | ⟨from registration⟩ / ⟨UP district⟩ |
| Team ID | ⟨TM…› captured in `docs/proof/portal/` |

---

## 1. Problem & user (≈120 words)

A first-time applicant for an income certificate in ⟨district⟩ usually does not know four
things before walking out of the house: which documents, at which office, what fee, and how
many days. In our field walkthrough of ⟨25⟩ people in ⟨ward/village⟩, ⟨X⟩ had already visited
an office once without the correct documents, ⟨Y⟩ paid an intermediary between ₹⟨A⟩ and ₹⟨B⟩
for information that is officially published, and ⟨Z⟩ could not say what happens after
submitting the form. The information exists; it is not findable, not in the user's language,
and not usable on a ₹8,000 Android phone with intermittent data.

## 2. Approach (≈180 words)

Seva-Marg is a **cited service catalogue plus a voice-first Android app**, deliberately split:
no language model writes an answer. A deterministic intent router maps a spoken or typed
question to a catalogue entry whose every field carries an official `source_url` and a
`verified_on` date. An answer composer renders five cards — eligibility, documents, fee,
office, SLA, and *what happens next* — and speaks them. When confidence is below the floor or
the entry is older than its staleness window, the app **refuses to guess** and routes the
citizen to the office type and the verified grievance channel instead. Scaling is a data
operation: a new service or district delta is a YAML file, not a code change.
Offline is a packaging fact, not a caching trick: the validated catalogue and the speech models
are assets **inside the APK**, so a fresh install's first launch needs no network at all. Hindi
speech input and output run **on the device** (Vosk ASR, offline TTS) — no cloud API and no
nearby laptop; a socket capture across a full journey records zero external requests.

## 3. Technology (≈120 words)

Kotlin + Jetpack Compose single-Activity app (Material 3, minSdk 26) over a Gradle multi-module
layout: `:app` (UI), **`:core` (pure Kotlin JVM: catalogue models, intent router, answer
composer, refusal router)**, `:voice` (Vosk-Android ASR + TTS engine interface), `:bench` (runs
the eval sets on the JVM, so benchmarks need no device and no network). Room stores session
history only — there are no name/phone/account fields in the schema; `RECORD_AUDIO` is the only
permission requested from the user, and `INTERNET` is declared solely for an optional
content-staleness check (the app is fully functional with it denied). The catalogue is authored
as citation-enforcing YAML compiled into
`assets/catalogue.json`; a JSON Schema validator **fails the build** on any uncited value, and a
build-time check enforces Hindi/English twins. Office locations come from a pre-bundled
OpenStreetMap extract (ODbL attribution on screen). Accessibility: WCAG 2.2 AA targets, Compose
semantics with a polite live region, ComposeAccessibilityChecks in CI, TalkBack-verified
voice-only mode. Every library and model licence is recorded in `NOTICES.md`.

## 4. Validation (≈150 words) — the part most entries skip

| Scope bullet | Method | Result |
|---|---|---|
| Reduce steps for a real public service | Official channel steps counted from the source page vs in-app taps/visits (adb/uiautomator + ⟨25⟩ respondents) | ⟨N⟩ steps → ⟨M⟩ steps; ⟨k⟩ visits avoided for ⟨x⟩/25 users |
| Work in Hindi and English | ⟨50⟩ Hindi + ⟨50⟩ English questions through `:bench`, exact-field match | accuracy ⟨p⟩ % / ⟨q⟩ % (parity Δ ⟨d⟩ pp) |
| Be usable on a basic smartphone | ⟨budget device⟩, Android ⟨v⟩, ⟨RAM⟩ GB; APK size ⟨mb⟩ MB sideloaded from QR; cold start `am start -W` | ⟨s⟩ s cold start, J1 completes in airplane mode, TalkBack works with screen off, **0 external requests** |
| Trust | ⟨15⟩ out-of-scope / garbled queries, typed and spoken into the phone's mic | ⟨15⟩/15 refused correctly, **0 invented facts** |
| Voice (on-device) | ⟨20⟩ recorded utterances across ⟨4⟩ accent groups, real mic on the budget phone | intent accuracy ⟨r⟩ %, end-to-end ⟨l⟩ s median, ASR/TTS entirely local |

n, method and uncontrolled variables are stated in the deck; the full protocol lives in
`docs/EVIDENCE_AND_DEMO.md` and every figure is regenerable with `scripts/verify-claims.sh`.

## 5. Impact & deployability (≈120 words)

Three certificate services covered today, chosen for volume and agent-mediation; ⟨4⟩ more
catalogue files extend it without code. Marginal cost per citizen is effectively ₹0 — the
catalogue and models ship inside the APK, there is no per-query cloud call and no backend to
run. Deployment needs no infrastructure: install the APK, or hand out ⟨10⟩ pre-loaded phones at
a CSC counter; a text-only lite APK (⟨mb⟩ MB) exists for low-storage devices.
For the department, the same catalogue is the artefact a citizen-charter page could publish, and
the local drop-off export shows *where* applicants get stuck. Requested pilot: one block or one
CSC cluster, with department co-verification of our catalogue fields and the
⟨screenshot-backed⟩ discrepancy list we found between official pages. Our build is additive
to the state's existing service portals, not a parallel portal.

## 6. Limitations (3 honest lines)

Three services, not twenty; one district's field study (n=⟨25⟩) — indicative, not
state-representative. On-device Hindi TTS is ⟨engine⟩ and sounds synthetic: we chose guaranteed
offline over a natural voice that needs a network, and ASR intent accuracy is ⟨r⟩ % measured on
real accents, with the failure list published. Content freshness is managed by a ⟨180⟩-day window
plus department co-verification, not live portal sync; the app is debug-signed and not on the
Play Store yet. We do not submit applications or take payments: we are the clarity layer *before*
the portal.

## 7. Links

- Demo video (public/unlisted, reachable 06.11–20.11): ⟨URL⟩ — verified from an incognito device
- Pitch deck PDF (≤ 20 MB): ⟨file⟩
- Repository: ⟨URL⟩ — README "reproduce in 5 commands", `results/` committed as evidence
- APK (debug-signed, ⟨size⟩ MB) + release notes + SHA256: ⟨GitHub Releases URL⟩ — installable by
  any Android phone, and by the evaluators' own phones

---

### Fill-order for 04.11 (do it top-down, no manual typing of numbers)

1. Run `scripts/bench-all.sh` (wraps `./gradlew :bench:run` + `adb` perf) → `results/` refreshed.
2. `stepcount` → §1 ⟨X/Y/Z⟩ and §4 row 1.
3. `qa_accuracy` → §4 row 2. `perf` → §4 row 3. `refusal` → §4 row 4. `asr_intent` → §4 row 5.
4. `docs/field/instrument.csv` → §1 user quote and §5 pilot framing.
5. Delete any sentence whose number could not be produced in step 2–4.
6. Word-count each section against the budget above; the portal form is unforgiving.
