# ADR-0002 · Native Android instead of PWA / React Native / Flutter

**Status:** accepted, 17.10.2026 (supersedes the PWA plan in v1.0 of `docs/BLUEPRINT.md`)
**Deciders:** M1 (lead), M2 (UI), M4 (voice) · triggers re-review only if the statement changes

## Context

UPNIC-004's third scope bullet is *"Be usable on a basic smartphone"*, and our product promise
includes **Hindi voice input that works without internet**. The original plan was an installable
React/TypeScript PWA with a service-worker precache, plus a LAN FastAPI bridge (Vosk + Piper) for
offline speech, and "on-device ASR" pushed to a Phase-5 stretch.

## Decision

Build a **native Android app: Kotlin + Jetpack Compose**, minSdk 26, single Activity, Gradle
multi-module (`:app` UI, `:core` pure Kotlin JVM, `:voice`, `:bench`). Speech models and the
validated catalogue ship as **assets inside the APK**. Distribution is a debug-signed APK via
GitHub Releases + QR — no Play Store, no server.

## Why (the three reasons that actually decided it)

1. **A browser cannot do offline speech recognition.** The Web Speech API streams audio to Google's
   servers. So a PWA either ships *no voice* in airplane mode or needs **a laptop on the same LAN**
   on the finale stage — and "offline, but bring a computer" is exactly the kind of caveat that
   costs credibility in a live defense. On Android, Vosk runs in-process: the phone alone does
   everything. This single capability was worth the platform change.
2. **The offline claim becomes structural instead of behavioural.** A precached service worker can
   fail (cache eviction, low-storage kill, an uncached asset, Chrome on a budget ROM). An asset
   inside the APK either installed or it did not. Our headline test — airplane mode, fresh install,
   full Hindi voice journey — is far harder to falsify.
3. **The demo wins a physical venue.** Judges can install the APK on *their own* phone from a QR
   code and speak Hindi at it. That is not reliably possible with a PWA on low-end handsets, and it
   turns the evaluation into the user's own device test.

## Alternatives rejected

| Option | Why not |
|---|---|
| **PWA (previous plan)** | No offline ASR; LAN-bridge compromise; install + cache behaviour unreliable on budget Android; no judge-installs story |
| **React Native / Expo** | Team comfort is real, but Vosk needs a hand-written native module anyway, Expo Go cannot load it (dev-build required), and TalkBack/semantics behave better with the framework you control. We would pay the native-module cost *and* keep a bridge layer |
| **Flutter** | Excellent accessibility primitives, but the team writes TypeScript/React, not Dart; the deterministic catalogue/intent layer gains nothing from Flutter here |
| **Capacitor (web app in a native shell)** | Keeps the WebView cache story alive (the thing we are removing) while still requiring a Kotlin Vosk plugin — the worst of both |
| **iOS at launch** | Out of scope: statement says basic smartphone, Indian mass-market reality is Android; iOS adds a second a11y stack (VoiceOver) for no rubric gain |

## Cost we accepted, and the mitigation

- **Kotlin ramp-up for a React/TS team — rated High risk.** Mitigations: `:core` is a **pure Kotlin
  JVM module** (no Android APIs), so the intent router, answer composer and refusal router are
  written and unit-tested like ordinary typed code with a fast test loop; UI stays at 5 routes with
  Material 3 defaults (no custom design system); Compose is declarative and close enough in spirit
  to the JSX the team already knows.
- **No hot-reload/web convenience; builds are slower.** Mitigated by running evals through `:bench`
  on the JVM (no emulator needed for accuracy/refusal benchmarks) and by keeping one physical device
  attached for UI work.
- **APK size** with bundled models (~40–80 MB ASR + TTS voice) vs a 3 MB web bundle. Mitigated by
  shipping a **text-only lite APK** too and putting both numbers on the slide with the reason.
- **Signing/store absence.** Mitigated by publishing the APK hash + source, and by framing the
  pilot ask as the path to a signed release.

## Consequences

- Phase 1 gains a **timeboxed Android + voice spike** (see ADR-0003); the budget test phone moves
  onto the critical path **today**.
- Dropped from the stack: Vite/Workbox/Dexie/axe-core/Playwright/FastAPI voice bridge.
  Replacements: Compose + Room + `ComposeAccessibilityChecks` + Espresso/UI Automator + `:bench`.
- `NOTICES.md` gains eSpeak-NG's GPL-3 question (see ADR-0003) and the Vosk model-weight check.
- Every "tier 2 needs a laptop" sentence in the docs is now wrong and has been rewritten; the deck's
  claim is *"voice runs inside the phone"* — and `netcheck.py` proves it with a socket capture.
