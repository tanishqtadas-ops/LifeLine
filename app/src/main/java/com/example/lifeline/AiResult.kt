package com.example.lifeline

import android.content.Context
import android.util.Log

/**
 * Task 5: Clean, decoupled output structure of the LifeLine offline AI pipeline.
 *
 * Represents the final result after:
 * Vosk transcript → TextEmbeddingEngine → ProtocolRetriever → ConfidencePolicy → AiResult
 *
 * This object is designed to be consumed by the UI / Application layer (Tanishq's Android module)
 * without exposing internal embedding or vector search mechanics.
 *
 * @param query            The raw user transcript processed.
 * @param decision         Final engineering status: [RetrievalDecision.VERIFIED], [RetrievalDecision.UNKNOWN], or [RetrievalDecision.AMBIGUOUS].
 * @param protocolId       Matched protocol identifier if VERIFIED, or null if UNKNOWN / AMBIGUOUS.
 * @param title            Human-readable protocol title if VERIFIED, or null.
 * @param response         Controlled emergency guidance text if VERIFIED, or null. (All Phase 1 text is placeholder awaiting authoritative medical review).
 * @param reference        Authoritative citation / source reference if VERIFIED, or null.
 * @param version          Protocol schema version if VERIFIED, or null.
 * @param topScore         Top similarity score if available.
 * @param runnerUpScore    Runner-up similarity score if available.
 * @param gap              Score difference between top match and runner-up.
 * @param reasonCode       Detailed rule / reason code explaining the decision (especially for UNKNOWN / AMBIGUOUS).
 * @param detectedSignals  List of safety concepts detected in the transcript.
 */
data class AiResult(
    val query: String,
    val decision: RetrievalDecision,
    val protocolId: String?,
    val title: String?,
    val response: String?,
    val reference: String?,
    val version: Int?,
    val topScore: Float?,
    val runnerUpScore: Float?,
    val gap: Float?,
    val reasonCode: String?,
    val detectedSignals: List<String> = emptyList()
) {
    /** True if the result is safely verified and ready to display emergency guidance. */
    val isVerified: Boolean get() = decision == RetrievalDecision.VERIFIED

    /** True if the result is ambiguous or contradictory and requires user clarification. */
    val isAmbiguous: Boolean get() = decision == RetrievalDecision.AMBIGUOUS

    /** True if the query is out of supported scope or has insufficient evidence. */
    val isUnknown: Boolean get() = decision == RetrievalDecision.UNKNOWN
}

/**
 * Service adapter that manages the offline LifeLine AI pipeline:
 * Speech Transcript -> ProtocolRetriever -> ConfidencePolicy -> AiResult.
 */
class LifeLineAiEngine(context: Context) {

    companion object {
        private const val TAG = "LifeLineAiEngine"
    }

    private val retriever = ProtocolRetriever(context)
    private val confidencePolicy = ConfidencePolicy()

    /**
     * Executes the end-to-end AI retrieval and safety policy pipeline for a given [transcript].
     *
     * Returns a structured [AiResult].
     */
    fun process(transcript: String): AiResult {
        if (transcript.isBlank()) {
            Log.w(TAG, "process: empty or blank transcript received")
            return AiResult(
                query = transcript,
                decision = RetrievalDecision.UNKNOWN,
                protocolId = null,
                title = null,
                response = null,
                reference = null,
                version = null,
                topScore = 0f,
                runnerUpScore = 0f,
                gap = 0f,
                reasonCode = "EMPTY_TRANSCRIPT: transcript is blank",
                detectedSignals = emptyList()
            )
        }

        val match = retriever.findBestMatch(transcript)
        val decision = confidencePolicy.evaluate(transcript, match)
        val gap = match.score - match.runnerUpScore

        Log.i(TAG, "Processed \"$transcript\" -> decision=${decision.decision} reason=${decision.reason}")

        return when (decision.decision) {
            RetrievalDecision.VERIFIED -> {
                val protocol = match.protocol
                AiResult(
                    query = transcript,
                    decision = RetrievalDecision.VERIFIED,
                    protocolId = protocol.protocolId,
                    title = protocol.title,
                    response = protocol.response,
                    reference = protocol.reference,
                    version = protocol.version,
                    topScore = match.score,
                    runnerUpScore = match.runnerUpScore,
                    gap = gap,
                    reasonCode = decision.reason,
                    detectedSignals = decision.detectedSignals
                )
            }
            RetrievalDecision.AMBIGUOUS -> {
                AiResult(
                    query = transcript,
                    decision = RetrievalDecision.AMBIGUOUS,
                    protocolId = null,
                    title = null,
                    response = null, // Never return unverified medical instructions
                    reference = null,
                    version = null,
                    topScore = match.score,
                    runnerUpScore = match.runnerUpScore,
                    gap = gap,
                    reasonCode = decision.reason,
                    detectedSignals = decision.detectedSignals
                )
            }
            RetrievalDecision.UNKNOWN -> {
                AiResult(
                    query = transcript,
                    decision = RetrievalDecision.UNKNOWN,
                    protocolId = null,
                    title = null,
                    response = null, // Never return unverified medical instructions
                    reference = null,
                    version = null,
                    topScore = match.score,
                    runnerUpScore = match.runnerUpScore,
                    gap = gap,
                    reasonCode = decision.reason,
                    detectedSignals = decision.detectedSignals
                )
            }
        }
    }

    /**
     * Releases underlying embedding resources.
     */
    fun close() {
        retriever.close()
        Log.i(TAG, "LifeLineAiEngine closed.")
    }
}
