package com.example.lifeline

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions

/**
 * Isolated engine for generating Text Embeddings using MediaPipe and
 * the Universal Sentence Encoder (USE) model.
 */
class TextEmbeddingEngine(private val context: Context) {

    companion object {
        private const val TAG = "TextEmbeddingEngine"
        private const val MODEL_PATH = "universal_sentence_encoder.tflite"
    }

    private var textEmbedder: TextEmbedder? = null

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)
                .build()

            val options = TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .build()

            textEmbedder = TextEmbedder.createFromOptions(context, options)
            Log.i(TAG, "TextEmbedder successfully initialized with USE model.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextEmbedder: ${e.message}", e)
        }
    }

    /**
     * Generates a numeric embedding vector (FloatArray) for the given input string.
     */
    fun embed(text: String): FloatArray {
        val embedder = textEmbedder
        if (embedder == null) {
            Log.e(TAG, "TextEmbedder is not initialized.")
            return FloatArray(0)
        }

        return try {
            val result = embedder.embed(text)
            // Extract the embedding vector from the first result
            result.embeddingResult().embeddings().first().floatEmbedding()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to embed text: ${e.message}", e)
            FloatArray(0)
        }
    }

    /**
     * Release resources when this engine is no longer needed.
     */
    fun close() {
        textEmbedder?.close()
        textEmbedder = null
        Log.i(TAG, "TextEmbedder closed.")
    }
}
