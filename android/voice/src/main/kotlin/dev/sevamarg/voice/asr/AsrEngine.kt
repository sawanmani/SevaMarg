// android/voice/src/main/kotlin/dev/sevamarg/voice/asr/AsrEngine.kt
//
// The contract :app codes against. Everything about it is a consequence of two project rules:
//
// 1. Offline first. initialise() loads a model that is already inside the APK or copied into
//    internal storage by scripts/fetch-models.sh. Nothing here may reach a network socket; an engine
//    that needs the cloud is not an implementation of this interface, it is a different product.
// 2. Never hold the mic. An open microphone on a government-services app reads as a spy app to a
//    judge and to the user's privacy indicator. start()/stop() are therefore part of the interface
//    rather than an implementation detail, and the model is initialised on the FIRST press, not at
//    launch (Phase 3.1) - launch cost stays inside the < 2 s cold-start budget.
package dev.sevamarg.voice.asr

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

enum class AsrStatus {
    /** No engine has been chosen yet - ADR-0003 is still open until the Phase 1.1 spike reports. */
    NO_ENGINE_SELECTED,

    /** The engine is known but its model is not on this device. */
    MODEL_MISSING,

    /** Model loaded, ready to be handed the mic. */
    READY,

    /** Currently capturing. */
    LISTENING,

    /** The engine failed at runtime (model corrupt, out of memory on a 2 GB device). */
    FAILED,
}

/**
 * A partial or final transcription. [isFinal] matters because the UI shows the transcript while the
 * person is still speaking (users trust a visible transcription, Phase 3.3), and only a final result is
 * handed to :core for routing.
 */
data class Transcript(val text: String, val isFinal: Boolean)

interface AsrEngine {

    val status: AsrStatus

    /**
     * Load the model for [languageCode] ("hi" | "en"). [onProgress] goes 0f..1f and is only for the
     * progress state in the UI; it may be called from a worker thread.
     */
    suspend fun initialise(languageCode: String, onProgress: (Float) -> Unit = {})

    /** Open the mic and stream results. Completes/cancels when the utterance ends or [stop] is called. */
    fun utterances(): Flow<Transcript>

    /** Close the mic immediately. Safe to call when not listening. */
    fun stop()

    /** Release native memory. The app calls this when the screen goes away for good. */
    fun release()
}

/**
 * The Phase 1 default. :app renders i18n key `ask.voice_unavailable` and a working text box instead of
 * a fake mic, so nothing in the demo claims a capability the spike has not measured yet.
 */
object NoEngineSelected : AsrEngine {
    override val status: AsrStatus get() = AsrStatus.NO_ENGINE_SELECTED

    override suspend fun initialise(languageCode: String, onProgress: (Float) -> Unit) = Unit

    // A cold interface has to be instantiable, and an empty stream is the honest answer: no model, no
    // transcript. The UI never subscribes to it because status is not READY.
    override fun utterances(): Flow<Transcript> = emptyFlow()

    override fun stop() = Unit

    override fun release() = Unit
}
