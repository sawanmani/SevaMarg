// android/voice/src/main/kotlin/dev/sevamarg/voice/tts/TtsEngine.kt
//
// Note what is NOT in this interface: any method that takes an Answer and returns a sentence. The
// wording is composed in :core (SpeechTemplates) from i18n + catalogue, because a Hindi fee sentence
// assembled by a TTS library is a fabricated fact with a voice attached to it. :voice speaks strings.
package dev.sevamarg.voice.tts

import dev.sevamarg.core.catalogue.Lang

enum class TtsStatus {
    /** No offline Hindi engine has been chosen - ADR-0003 is open until the Phase 1.1 spike reports. */
    NO_ENGINE_SELECTED,

    /** Engine present, voice data missing on this device. */
    VOICE_MISSING,

    READY,
    SPEAKING,

    /**
     * Only for Android's built-in TextToSpeech, and only when a Hindi voice is installed on the phone.
     * Opportunistic extra (Phase 3.2), never the baseline: the built-in engine usually needs Google
     * TTS + a network fetch on a fresh budget device, which the offline promise cannot depend on.
     */
    SYSTEM_ENGINE_AVAILABLE,

    FAILED,
}

interface TtsEngine {

    val status: TtsStatus

    /**
     * Speak [text] - already final, already in [lang], already free of `{placeholders}` (SpeechTemplates
     * drops anything it cannot resolve rather than reading a raw i18n key aloud).
     *
     * [onDone] fires when playback finishes or is interrupted. The voice state machine in :app needs it
     * to leave the "speaking" state; a speech engine that never calls back strands the UI.
     */
    fun speak(text: String, lang: Lang, onDone: () -> Unit = {})

    /** Stop immediately and make the mic button available again. */
    fun stop()

    fun release()
}

/** The Phase 1 default: the answer is on screen and readable by TalkBack, but the app does not speak. */
object NoEngineSelected : TtsEngine {
    override val status: TtsStatus get() = TtsStatus.NO_ENGINE_SELECTED

    override fun speak(text: String, lang: Lang, onDone: () -> Unit) = onDone()

    override fun stop() = Unit

    override fun release() = Unit
}
