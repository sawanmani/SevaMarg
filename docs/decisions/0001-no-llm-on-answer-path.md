# ADR-0001 · No LLM on the answer path

**Status:** accepted, 17.10.2026 · owner: M1 · re-review only if the catalogue coverage drops below
what a deterministic router can address

## Decision

Every answer a citizen sees or hears is produced by **deterministic code over cited data**:
`:core/intent` (keyword + slot matcher with Devanagari and Hinglish transliteration normalisation)
→ catalogue lookup → `:core/compose` (typed `Answer` built from catalogue fields) →
`:core/refusal` (confidence floor 0.85, `stale_after_days` window). **No generative model writes a
fee, a document name, an office, or an SLA.**

LLM/translation models are allowed **only offline, in content preparation**, with a human in the
loop: AI4Bharat IndicTrans2 for drafting Hindi paraphrases, which a reviewer approves and which are
then **frozen as catalogue text**. At runtime the app reads strings; it does not generate them.

## Why

1. **Failure mode.** In a governance service, a hallucinated fee is not a bug, it is harm — the user
   shows up at a tehsil with the wrong money or the wrong document and loses a day. A wrong answer
   from a cited field is a *data* problem with an owner and a fix; a wrong answer from a model is
   unbounded.
2. **It is the first attack in the viva.** "What if it states the wrong fee?" has a one-sentence
   answer here: *it cannot, the model never writes the answer, and here is the validator that fails
   the build on an uncited field.* Judges can check that claim in the code and in `results/`.
3. **It makes the refusal path real.** Uncertainty is a first-class state (`confidence_floor`,
   staleness) rather than something a generative model papers over with fluent prose. 15/15 refused
   with 0 invented facts is a benchmark we can actually pass and print.
4. **Cost/deployability.** Deterministic matching on-device is microseconds and ₹0 per query; there
   is no token bill and no network dependency, which is what "usable on a basic smartphone" needs.

## What we give up

Free-form phrasing robustness ("mera beta hai uske liye jo milta hai wo bata do") and automatic
handling of unseen question shapes. Accepted: the quick-reply chips, the alias table in the
catalogue, and the refusal path cover the gap honestly. We will publish the intent-accuracy number
and the failure list rather than pretend the router is a chatbot.

## Consequences

- `:core` needs no network permission and no model file beyond the speech engines.
- Every ambiguous user phrasing that fails becomes either a **catalogue alias** or an **OOS eval
  item** — both are data edits, not code edits.
- The deck's novelty claim is the architecture (cited catalogue + refusal), not "we used AI".
- Related: `0002-native-android-over-pwa.md`, `0003-voice-engines.md` (speech models are input/output
  converters only — they still never author an answer).
