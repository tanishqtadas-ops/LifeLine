package com.example.lifeline

import android.util.Log

// ── Decision outcomes ──────────────────────────────────────────────────────────

/**
 * Engineering decision produced by the [ConfidencePolicy].
 *
 * This is NOT a medical certainty score.
 * It is a retrieval confidence classification for LifeLine Phase 1.
 */
enum class RetrievalDecision {
    /** Evidence is sufficient and unambiguous. Safe to surface the protocol. */
    VERIFIED,

    /** Conflicting or boundary evidence detected. Clarification is required. */
    AMBIGUOUS,

    /** Semantic score too low or out of supported domain scope. */
    UNKNOWN
}

/**
 * Complete decision record produced by the confidence evaluation.
 *
 * @param decision        Final engineering decision.
 * @param match           The underlying [ProtocolMatch] from [ProtocolRetriever].
 * @param reason          Explanation of which rule triggered this decision.
 * @param detectedSignals Human-readable list of safety concepts found in the transcript.
 */
data class ConfidenceDecision(
    val decision: RetrievalDecision,
    val match: ProtocolMatch,
    val reason: String,
    val detectedSignals: List<String>
)

// ── Internal signal bundle ─────────────────────────────────────────────────────

private data class SafetySignals(
    val positiveBreathing: Boolean,    // "is breathing / started breathing / breathing now"
    val negatedBreathing: Boolean,     // "not breathing / stopped breathing"
    val notResponding: Boolean,        // "not responding / unresponsive / won't wake"
    val positiveResponding: Boolean,   // "is responding / responsive / awake / answering"
    val outOfScope: Boolean,           // "heart attack / medicine / disease / blood pressure / medication"
    val labels: List<String>
) {
    /**
     * True when the transcript contains BOTH a positive-breathing signal AND a
     * not-responding signal, or BOTH a positive AND a negated breathing signal.
     * Either combination indicates contradictory clinical information that the
     * retriever cannot safely resolve.
     */
    val hasContradiction: Boolean
        get() = (positiveBreathing && notResponding) ||
                (negatedBreathing && positiveBreathing)
}

// ── Policy class ──────────────────────────────────────────────────────────────

/**
 * Task 4B: Safety-oriented confidence / UNKNOWN decision layer.
 *
 * Sits above [ProtocolRetriever] and converts a raw [ProtocolMatch] + the
 * original transcript into a [ConfidenceDecision] conservative enough for
 * LifeLine Phase 1.
 *
 * ## Decision constants
 * All thresholds are named constants in the companion object so they can be
 * tuned on device data without hunting through logic branches.
 *
 * ## Decision rules (evaluated in order; first match wins)
 * 1. Out-of-supported scope check                             → UNKNOWN (OUT_OF_SUPPORTED_SCOPE)
 * 2. Unsupported positive responding concept                  → AMBIGUOUS (UNSUPPORTED_POSITIVE_RESPONDING)
 * 3. score < MIN_SCORE_VERIFIED                               → UNKNOWN (SCORE_BELOW_MINIMUM)
 * 4. Contradictory safety signals in transcript               → AMBIGUOUS (CONTRADICTORY_SIGNALS)
 * 5. Negated-breathing matched to CPR_BREATHING               → AMBIGUOUS (NEGATED_BREATHING_MISMATCH)
 * 6. Negated-breathing without explicit not-responding        → AMBIGUOUS (NEGATED_BREATHING_INSUFFICIENT_INFO)
 * 7. Safety-critical pair AND gap < MIN_GAP_SAFETY_CRITICAL   → AMBIGUOUS (SAFETY_CRITICAL_GAP_TOO_SMALL)
 * 8. gap < MIN_GAP_VERIFIED                                   → AMBIGUOUS (GAP_TOO_SMALL)
 * 9. (all checks passed)                                      → VERIFIED
 */
class ConfidencePolicy {

    companion object {
        private const val TAG = "ConfidencePolicy"

        // ── Configurable decision constants ───────────────────────────────────

        /**
         * Minimum final score (semantic + safety signal) for VERIFIED.
         */
        const val MIN_SCORE_VERIFIED = 0.83f

        /**
         * Minimum gap (top score − runner-up) for VERIFIED on non-critical pairs.
         */
        const val MIN_GAP_VERIFIED = 0.04f

        /**
         * Stricter gap threshold for the CPR_BREATHING ↔ CPR_NOT_RESPONDING pair.
         */
        const val MIN_GAP_SAFETY_CRITICAL_PAIR = 0.07f
    }

    /**
     * Protocol pairs where ambiguity is especially dangerous.
     * These require [MIN_GAP_SAFETY_CRITICAL_PAIR] instead of [MIN_GAP_VERIFIED].
     */
    private val safetyCriticalPairs: Set<Set<String>> = setOf(
        setOf("CPR_BREATHING", "CPR_NOT_RESPONDING")
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Evaluates [match] against [transcript] and returns a [ConfidenceDecision].
     *
     * [transcript] is the raw STT output. It is used exclusively for safety
     * signal detection — the semantic scoring has already been done by
     * [ProtocolRetriever].
     */
    fun evaluate(transcript: String, match: ProtocolMatch): ConfidenceDecision {
        val signals = detectSafetySignals(transcript)
        val gap     = match.score - match.runnerUpScore
        val topId   = match.protocol.protocolId
        val pair    = setOf(topId, match.runnerUpId)
        val isSafetyCritical = safetyCriticalPairs.any { it == pair }

        Log.i(TAG, "evaluate: \"$transcript\"")
        Log.i(TAG, "  top=$topId score=${fmt(match.score)}  runner=${match.runnerUpId} " +
            "score=${fmt(match.runnerUpScore)}  gap=${fmt(gap)}")
        Log.i(TAG, "  safetyCritical=$isSafetyCritical  signals=${signals.labels}")

        // Rule 1 ── Out of supported domain scope → UNKNOWN
        if (signals.outOfScope) {
            return decide(RetrievalDecision.UNKNOWN, match, signals,
                "OUT_OF_SUPPORTED_SCOPE: query asks for unsupported diagnosis, medication, or measurements")
        }

        // Rule 2 ── Positive responding/responsive concept → AMBIGUOUS (never VERIFIED)
        if (signals.positiveResponding) {
            return decide(RetrievalDecision.AMBIGUOUS, match, signals,
                "UNSUPPORTED_POSITIVE_RESPONDING: patient is reported responsive; does not map to emergency CPR protocol")
        }

        // Rule 3 ── Score too low → UNKNOWN (off-topic / out-of-domain)
        if (match.score < MIN_SCORE_VERIFIED) {
            return decide(RetrievalDecision.UNKNOWN, match, signals,
                "SCORE_BELOW_MINIMUM: Score ${fmt(match.score)} < MIN_SCORE_VERIFIED ($MIN_SCORE_VERIFIED)")
        }

        // Rule 4 ── Contradictory critical signals → AMBIGUOUS
        if (signals.hasContradiction) {
            return decide(RetrievalDecision.AMBIGUOUS, match, signals,
                "CONTRADICTORY_SIGNALS: Contradictory signals detected: ${signals.labels.joinToString()}")
        }

        // Rule 5 ── Negated breathing matched to CPR_BREATHING → AMBIGUOUS
        if (signals.negatedBreathing && topId == "CPR_BREATHING") {
            return decide(RetrievalDecision.AMBIGUOUS, match, signals,
                "NEGATED_BREATHING_MISMATCH: Negated-breathing signal conflicts with top match CPR_BREATHING")
        }

        // Rule 6 ── Negated breathing without explicit not-responding → AMBIGUOUS
        if (signals.negatedBreathing && !signals.notResponding) {
            return decide(RetrievalDecision.AMBIGUOUS, match, signals,
                "NEGATED_BREATHING_INSUFFICIENT_INFO: Negated-breathing present but no not-responding signal")
        }

        // Rule 7 ── Safety-critical pair with insufficient gap → AMBIGUOUS
        if (isSafetyCritical && gap < MIN_GAP_SAFETY_CRITICAL_PAIR) {
            return decide(RetrievalDecision.AMBIGUOUS, match, signals,
                "SAFETY_CRITICAL_GAP_TOO_SMALL: Pair $topId↔${match.runnerUpId} gap ${fmt(gap)} < $MIN_GAP_SAFETY_CRITICAL_PAIR")
        }

        // Rule 8 ── Generic gap too small → AMBIGUOUS
        if (gap < MIN_GAP_VERIFIED) {
            return decide(RetrievalDecision.AMBIGUOUS, match, signals,
                "GAP_TOO_SMALL: Gap ${fmt(gap)} < MIN_GAP_VERIFIED ($MIN_GAP_VERIFIED)")
        }

        // All checks passed → VERIFIED
        return decide(RetrievalDecision.VERIFIED, match, signals,
            "VERIFIED: Score ${fmt(match.score)} gap ${fmt(gap)} — all safety rules passed")
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun decide(
        decision: RetrievalDecision,
        match: ProtocolMatch,
        signals: SafetySignals,
        reason: String
    ): ConfidenceDecision {
        Log.i(TAG, "  → $decision  reason: $reason")
        return ConfidenceDecision(decision, match, reason, signals.labels)
    }

    /**
     * Detects safety-relevant concepts in the transcript.
     */
    private fun detectSafetySignals(text: String): SafetySignals {
        val t = text.lowercase()
        val labels = mutableListOf<String>()

        // ── Out-of-scope domain keywords ───────────────────────────────────
        val outOfScopeTokens = listOf(
            "heart attack", "cardiac arrest diagnosis", "stroke", "seizure",
            "medicine", "medication", "drug", "drugs", "dosage", "pill", "pills",
            "disease", "illness", "diagnos", "blood pressure", "temperature",
            "oxygen level", "thermometer", "oximeter"
        )
        val outOfScope = outOfScopeTokens.any { t.contains(it) }
        if (outOfScope) labels.add("OUT_OF_SCOPE")

        // ── Positive responding (patient is responding/awake) ───────────────
        // Distinguish from negative: "not responding", "unresponsive", etc.
        val negativeRespondingTokens = listOf(
            "not respond", "no response", "unresponsive", "not waking",
            "won't wake", "will not wake", "cannot wake", "can't wake",
            "unconscious", "not reacting", "out cold", "not conscious", "non responsive"
        )
        val notResponding = negativeRespondingTokens.any { t.contains(it) }
        if (notResponding) labels.add("NOT_RESPONDING")

        val positiveRespondingTokens = listOf(
            "is responding", "are responding", "seems responsive", "is responsive",
            "awake and answering", "answering me", "talk to me", "talking to me",
            "can hear me", "reacted to me", "responding to me"
        )
        // Only trigger positive responding if it's not negated
        val positiveResponding = !notResponding && positiveRespondingTokens.any { t.contains(it) }
        if (positiveResponding) labels.add("POSITIVE_RESPONDING")

        // ── Positive breathing (patient is breathing) ──────────────────────
        val negatedBreathingTokens = listOf(
            "not breath", "no breath", "isn't breath", "not breathing",
            "stopped breath", "no longer breath", "cannot breathe", "can't breathe",
            "struggling to breathe"
        )
        val negatedBreathing = negatedBreathingTokens.any { t.contains(it) }
        if (negatedBreathing) labels.add("NEGATED_BREATHING")

        val positiveBreathingTokens = listOf(
            "is breathing", "are breathing", "started breathing", "breathing now",
            "breathing on their own", "breathing on her own", "breathing on his own",
            "breathing normally", "taking breath", "feel breath", "feel air",
            "chest is moving", "chest moving", "going up and down"
        )
        val positiveBreathing = !negatedBreathing && positiveBreathingTokens.any { t.contains(it) }
        if (positiveBreathing) labels.add("POSITIVE_BREATHING")

        return SafetySignals(
            positiveBreathing = positiveBreathing,
            negatedBreathing = negatedBreathing,
            notResponding = notResponding,
            positiveResponding = positiveResponding,
            outOfScope = outOfScope,
            labels = labels
        )
    }

    private fun fmt(v: Float) = "%.4f".format(v)
}
