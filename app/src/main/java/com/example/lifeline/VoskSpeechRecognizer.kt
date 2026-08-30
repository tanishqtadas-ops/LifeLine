package com.example.lifeline

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.json.JSONObject
import java.io.IOException

/**
 * Isolated offline Speech-to-Text module using Vosk.
 *
 * This class is fully independent of any Activity or UI framework.
 * It can be injected into the larger LifeLine app when ready.
 *
 * Usage:
 *   val stt = VoskSpeechRecognizer(context, listener)
 *   stt.initModel()          // call once; listener.onModelLoaded() fires when ready
 *   stt.startListening()     // begin recognition
 *   stt.stopListening()      // stop recognition
 *   stt.destroy()            // release all resources
 */
class VoskSpeechRecognizer(
    private val context: Context,
    private val listener: SttListener
) {

    companion object {
        private const val TAG = "VoskSTT"
        private const val MODEL_ASSET_NAME = "model-en-us"
        private const val SAMPLE_RATE = 16000.0f
    }

    // ── Public callback interface ──────────────────────────────────────

    interface SttListener {
        /** Called with partial (live) transcription while user is still speaking. */
        fun onPartialResult(text: String)

        /** Called with a finalized sentence after a speech pause. */
        fun onResult(text: String)

        /** Called when the model has been loaded and STT is ready. */
        fun onModelLoaded()

        /** Called on any error (model load failure, mic error, etc.). */
        fun onError(message: String)
    }

    // ── State ──────────────────────────────────────────────────────────

    private var model: Model? = null
    private var speechService: SpeechService? = null

    /** True while actively capturing microphone audio. */
    var isListening: Boolean = false
        private set

    /** True after the Vosk model has been unpacked and loaded. */
    var isModelLoaded: Boolean = false
        private set

    // ── Model loading ──────────────────────────────────────────────────

    /**
     * Unpacks the Vosk model from assets to internal storage (first run only)
     * and initializes the [Model] object.
     *
     * This is asynchronous — [SttListener.onModelLoaded] fires on success,
     * [SttListener.onError] on failure.
     */
    fun initModel() {
        StorageService.unpack(
            context,
            MODEL_ASSET_NAME,
            MODEL_ASSET_NAME,
            /* completeCallback = */ { loadedModel ->
                model = loadedModel
                isModelLoaded = true
                Log.i(TAG, "Vosk model loaded successfully")
                listener.onModelLoaded()
            },
            /* errorCallback = */ { exception ->
                val msg = "Failed to load Vosk model: ${exception.message}"
                Log.e(TAG, msg, exception)
                listener.onError(msg)
            }
        )
    }

    // ── Start / Stop ───────────────────────────────────────────────────

    /**
     * Begin capturing audio from the microphone and streaming it to Vosk.
     *
     * Requires [initModel] to have completed successfully first.
     */
    fun startListening() {
        if (!isModelLoaded || model == null) {
            listener.onError("Model not loaded yet. Call initModel() first.")
            return
        }

        if (isListening) {
            Log.w(TAG, "Already listening — ignoring duplicate start.")
            return
        }

        try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE).also { service ->
                service.startListening(recognitionListener)
                isListening = true
                Log.i(TAG, "Speech recognition started")
            }
        } catch (e: IOException) {
            val msg = "Failed to start microphone: ${e.message}"
            Log.e(TAG, msg, e)
            listener.onError(msg)
        }
    }

    /**
     * Stop capturing audio. Any in-progress utterance is finalized.
     */
    fun stopListening() {
        speechService?.run {
            stop()
            shutdown()
        }
        speechService = null
        isListening = false
        Log.i(TAG, "Speech recognition stopped")
    }

    /**
     * Release all resources. Call this in Activity.onDestroy() or when
     * the STT module is no longer needed.
     */
    fun destroy() {
        stopListening()
        model?.close()
        model = null
        isModelLoaded = false
        Log.i(TAG, "VoskSpeechRecognizer destroyed")
    }

    // ── Internal Vosk listener ─────────────────────────────────────────

    private val recognitionListener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            val text = extractText(hypothesis, key = "partial")
            if (text.isNotBlank()) {
                listener.onPartialResult(text)
            }
        }

        override fun onResult(hypothesis: String?) {
            val text = extractText(hypothesis, key = "text")
            if (text.isNotBlank()) {
                listener.onResult(text)
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            val text = extractText(hypothesis, key = "text")
            if (text.isNotBlank()) {
                listener.onResult(text)
            }
        }

        override fun onError(exception: Exception?) {
            val msg = "Recognition error: ${exception?.message ?: "unknown"}"
            Log.e(TAG, msg, exception)
            listener.onError(msg)
        }

        override fun onTimeout() {
            Log.w(TAG, "Recognition timed out")
            stopListening()
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Vosk returns results as JSON: {"text":"hello world"} or {"partial":"hello"}.
     * This helper extracts the value for the given key.
     */
    private fun extractText(json: String?, key: String): String {
        if (json.isNullOrBlank()) return ""
        return try {
            JSONObject(json).optString(key, "").trim()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Vosk JSON: $json", e)
            ""
        }
    }
}
