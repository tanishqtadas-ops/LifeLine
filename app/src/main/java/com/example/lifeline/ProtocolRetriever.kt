package com.example.lifeline

import android.content.Context
import android.util.Log

/**
 * Task 3C: Improved LifeLine Protocol Retriever.
 *
 * Two-layer scoring architecture:
 *
 * Layer 1 — Semantic Max-Pool:
 *   Each protocol stores all individual example embeddings (NOT averaged into a centroid).
 *   The query is scored against every example and the MAX similarity is used as the
 *   semantic score. This prevents a strong vomiting query from being diluted by a
 *   centroid that also contains breathing/responding example phrases.
 *
 * Layer 2 — Safety-Aware Signal:
 *   A lightweight concept-detection layer that provides a small additive boost
 *   (capped at ±MAX_SIGNAL_BOOST) for critical disambiguation pairs:
 *     VOMITING vs BREATHING
 *     NOT-RESPONDING vs BREATHING
 *     CPR-START vs CPR-RATE
 *     CPR-START vs WAITING-FOR-HELP
 *
 *   This is NOT a hardcoded sentence matcher — the semantic embedding remains the
 *   primary signal. The safety layer only resolves genuinely marginal cases.
 *
 * Public API is identical to the previous version.
 */
class ProtocolRetriever(context: Context) {

    companion object {
        private const val TAG = "ProtocolRetriever"
        // Maximum absolute value the safety layer can contribute to any score.
        // Kept deliberately small so it can only tip marginal cases.
        private const val MAX_SIGNAL_BOOST = 0.15f
    }

    private val engine = TextEmbeddingEngine(context)

    // Individual example embeddings per protocol (NOT averaged).
    private val protocolEmbeddings: Map<String, List<FloatArray>>

    init {
        Log.i(TAG, "Task 3C: pre-computing individual example embeddings…")
        protocolEmbeddings = buildEmbeddings()
        val totalVecs = protocolEmbeddings.values.sumOf { it.size }
        Log.i(TAG, "Ready. $totalVecs total example vectors across ${protocolEmbeddings.size} protocols.")
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Finds the best-matching protocol for [transcript].
     *
     * Scoring = max(cosine_sim(query, example) for example in protocol) + safetySignal(...)
     *
     * No confidence threshold is applied — the caller receives raw final scores.
     */
    fun findBestMatch(transcript: String): ProtocolMatch {
        val queryVec = engine.embed(transcript)
        if (queryVec.isEmpty()) {
            Log.e(TAG, "findBestMatch: embedding failed for transcript=\"$transcript\"")
            val first = ProtocolKnowledgeBase.protocols.first()
            return ProtocolMatch(first, 0f, "", 0f)
        }

        val scores: List<Pair<LifeLineProtocol, Float>> = ProtocolKnowledgeBase.protocols
            .mapNotNull { protocol ->
                val examples = protocolEmbeddings[protocol.protocolId]
                if (examples.isNullOrEmpty()) return@mapNotNull null

                // Layer 1: semantic max-pool across all examples
                val semanticScore = examples.maxOf { engine.cosineSimilarity(queryVec, it) }

                // Layer 2: safety-aware signal (small additive adjustment)
                val signal = safetySignal(transcript, protocol.protocolId)
                    .coerceIn(-MAX_SIGNAL_BOOST, MAX_SIGNAL_BOOST)

                Pair(protocol, semanticScore + signal)
            }
            .sortedByDescending { it.second }

        val best     = scores[0]
        val runnerUp = scores.getOrNull(1)

        Log.i(TAG, "Query: \"$transcript\"")
        scores.forEachIndexed { i, (p, s) ->
            Log.i(TAG, "  [${i + 1}] ${p.protocolId}  finalScore=${"%.4f".format(s)}")
        }

        return ProtocolMatch(
            protocol      = best.first,
            score         = best.second,
            runnerUpId    = runnerUp?.first?.protocolId ?: "",
            runnerUpScore = runnerUp?.second ?: 0f
        )
    }

    /**
     * Release the underlying embedding engine.
     */
    fun close() {
        engine.close()
        Log.i(TAG, "ProtocolRetriever closed.")
    }

    // ── Layer 2: Safety-Aware Signal ───────────────────────────────────────────
    //
    // Returns a small float in [-MAX_SIGNAL_BOOST, +MAX_SIGNAL_BOOST].
    // Positive = more evidence for this protocol.
    // Negative = evidence against this protocol.

    private fun safetySignal(text: String, protocolId: String): Float {
        val t = text.lowercase()

        return when (protocolId) {

            "CPR_VOMITING" -> {
                // Strong lexical signals for vomiting — no semantic overlap with breathing
                val tokens = listOf(
                    "vomit", "threw up", "puke", "puked", "puking",
                    "regurgitat", "throwing up", "spit up", "spitting up",
                    "stuff is coming out", "something is coming out",
                    "coming out of their mouth", "coming out of his mouth",
                    "coming out of her mouth"
                )
                if (tokens.any { t.contains(it) }) 0.12f else 0f
            }

            "CPR_BREATHING" -> {
                // Boost when breathing is clearly POSITIVE.
                // Apply a penalty when breathing is explicitly NEGATED — those queries
                // belong to CPR_NOT_RESPONDING.
                val positiveBreathing = listOf(
                    "is breathing", "are breathing", "started breathing",
                    "breathing now", "breathing on their own", "breathing on her own",
                    "breathing on his own", "taking breath", "feel breath", "feel air",
                    "chest is moving", "chest moving", "chest rising", "going up and down",
                    "i can see them breathing", "i think they are breathing"
                )
                val negatedBreathing = listOf(
                    "not breath", "no breath", "isn't breath", "not breathing",
                    "stopped breath", "no longer breath", "not breathing and not"
                )
                when {
                    negatedBreathing.any { t.contains(it) } -> -0.12f
                    positiveBreathing.any { t.contains(it) } -> 0.10f
                    else -> 0f
                }
            }

            "CPR_NOT_RESPONDING" -> {
                val notRespondingTokens = listOf(
                    "not respond", "no response", "unresponsive", "not waking",
                    "won't wake", "will not wake", "cannot wake", "can't wake",
                    "unconscious", "not reacting", "no reaction",
                    "limp", "out cold", "not conscious", "collapsed"
                )
                val notBreathingTokens = listOf(
                    "not breath", "no breath", "stopped breath", "not breathing"
                )
                val notRespondingBoost = if (notRespondingTokens.any { t.contains(it) }) 0.10f else 0f
                val notBreathingBoost  = if (notBreathingTokens.any { t.contains(it) }) 0.06f else 0f
                notRespondingBoost + notBreathingBoost
            }

            "CPR_RATE" -> {
                val rateTokens = listOf(
                    "per minute", "how fast", "how quick", "rhythm", "bpm",
                    "beats per", "compressions per", "times per", "times a minute",
                    "too slow", "too fast", "how many", "rate", "speed",
                    "pace", "how quickly", "how rapidly"
                )
                if (rateTokens.any { t.contains(it) }) 0.10f else 0f
            }

            "CPR_START" -> {
                val howToTokens = listOf(
                    "how do i", "how to", "where do i", "where to place",
                    "walk me through", "guide me", "i don't know how",
                    "what do i do first", "tell me how", "show me how",
                    "need to start", "need to begin", "i need to do cpr"
                )
                val cprContextTokens = listOf(
                    "cpr", "compressions", "resuscitat", "hands on chest",
                    "where do i place", "chest compressions"
                )
                // Exclude if the question is really about RATE or WAITING
                val rateExcluders    = listOf("fast", "slow", "quick", "rate", "minute", "rhythm", "many", "speed")
                val waitingExcluders = listOf("ambulance", "paramedic", "waiting", "on the way", "on its way", "called")

                val isHowTo      = howToTokens.any { t.contains(it) }
                val hasCprCtx    = cprContextTokens.any { t.contains(it) }
                val isRateQuery  = rateExcluders.any { t.contains(it) }
                val isWaiting    = waitingExcluders.any { t.contains(it) }

                when {
                    isWaiting              -> 0f
                    isRateQuery            -> 0f
                    isHowTo && hasCprCtx   -> 0.13f
                    isHowTo                -> 0.06f
                    else                   -> 0f
                }
            }

            "WAITING_FOR_HELP" -> {
                val waitingTokens = listOf(
                    "ambulance", "paramedic", "on its way", "on the way",
                    "help is coming", "help is on", "emergency service",
                    "911", "112", "999", "called for help", "called the ambulance",
                    "waiting for help", "waiting for the"
                )
                if (waitingTokens.any { t.contains(it) }) 0.12f else 0f
            }

            else -> 0f
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun buildEmbeddings(): Map<String, List<FloatArray>> {
        val result = mutableMapOf<String, List<FloatArray>>()
        for (protocol in ProtocolKnowledgeBase.protocols) {
            val vecs = protocol.examplePhrases.mapNotNull { phrase ->
                val vec = engine.embed(phrase)
                if (vec.isEmpty()) {
                    Log.w(TAG, "  [${protocol.protocolId}] empty embedding for: \"$phrase\""); null
                } else vec
            }
            if (vecs.isEmpty()) {
                Log.e(TAG, "  [${protocol.protocolId}] no valid embeddings — skipping.")
            } else {
                result[protocol.protocolId] = vecs
                Log.i(TAG, "  [${protocol.protocolId}] stored ${vecs.size} example vectors  dim=${vecs.first().size}")
            }
        }
        return result
    }
}
