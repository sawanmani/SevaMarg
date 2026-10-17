# ADR-0003 · On-device speech engines (ASR + TTS) — **decision pending measurement**

**Status:** proposed, 17.10.2026 · to be **accepted by 21.10.2026** inside the Phase 1 spike
**Owner:** M4 · inputs: `results/voice-spike/`
**Rule:** this ADR is closed with numbers, not preference. If the spike slips past 21.10, the
fallback row below is adopted automatically and the slip is recorded here honestly.

## Question

Which engine speaks and listens to Hindi **inside the phone, with no network**, on a ₹8–10k
device with 2 GB RAM — at what accuracy, latency, APK cost and licence cost?

## Candidates

| Function | Candidate | Knowns | Unknowns (to measure) |
|---|---|---|---|
| ASR | **Vosk Android** + `vosk-model-small-hi-*` | Apache-2.0 code, streaming CPU decoder, model tens of MB, Kaldi lineage | Hindi WER on **our** 20 real utterances; intent accuracy after normalisation; init time; RAM peak |
| ASR (alt) | sherpa-onnx Zipformer/Paraformer Hindi | single AAR, active project | model size, Hindi model availability/licence, integration effort |
| TTS | **Piper voice via sherpa-onnx** | natural-ish, ONNX, offline | AAR/JNI integration time; Hindi phonemisation quality; voice-card licence per model |
| TTS | **eSpeak NG** (Hindi phonemizer) | tiny, offline, zero-risk to integrate | robotic quality; **GPL-3** implications if linked (see NOTICES.md) |
| TTS | Android `TextToSpeech` framework | free, already on many devices | Hindi engine presence is device-dependent; may require a download → **not allowed as the demo path** |

## Measurement plan (2 days, budget phone, airplane mode, no emulator audio)

1. Record 20 utterances (5 each: city Hindi, Awadh, Purvanchal, Bhojpur, one noisy environment).
2. For each ASR candidate: WER, **intent accuracy** (what the router decided), init ms after first
   mic press, end-to-end speech-to-text ms, peak RAM, model bytes.
3. For each TTS candidate: 5 listeners rate intelligibility of the same 6 answers (1–5), first-audio
   ms, bytes, licence row check.
4. Play all three TTS options on the phone over the venue's loudest-speaker setting → pick the one
   an elderly listener can follow.
5. Write the numbers into `results/voice-spike/spike.json`; `verify-claims.sh` must be able to
   regenerate them.

## Decision rule (fixed now, so nobody rationalises later)

- ASR: keep the candidate with **intent accuracy ≥ 85 %** on the 20-utterance set; if none reaches
  it, Vosk stays (open licence, simplest path) and voice becomes *supplementary* to the text path,
  stated in the deck.
- TTS: **Piper via sherpa-onnx if it integrates and passes by 24.10**, otherwise **eSpeak NG as a
  separate component** (licence handled per NOTICES.md), otherwise ship ASR + TalkBack as the
  spoken output. Never a cloud TTS.
- Model assets load on first mic press, never at launch — cold start < 2 s is non-negotiable.
- Record in this ADR: chosen engines + versions + model hashes + measured numbers + what we
  rejected and why, so slide 9 and `NOTICES.md` cite the same facts.

## Consequences we accept

A ~40–80 MB ASR asset and a TTS voice inside the APK → a large demo APK. Mitigated by a
text-only **lite APK** (Phase 5.1) and by publishing both sizes with the reason. A robotic Hindi
voice if eSpeak NG wins → accepted, because *"offline and intelligible"* is the requirement and
*"natural but online"* is not.
