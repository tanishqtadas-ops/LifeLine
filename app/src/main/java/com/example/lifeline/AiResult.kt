package com.example.lifeline

/**
 * Status of an AI-generated guidance result.
 *
 * VERIFIED — the semantic retrieval matched a protocol with HIGH confidence.
 *            The response text can be safely displayed to the user.
 *
 * UNKNOWN  — confidence was too low to return a specific protocol, or the
 *            query did not match anything in the local knowledge base.
 *            The UI must show a "cannot safely guide" message and direct the
 *            user to professional help.
 *
 * Future statuses (e.g. PARTIAL, ERROR) can be added here without changing
 * [AskLifeLineScreen]; add a new branch in the when() expression there.
 */
enum class AiResultStatus {
    VERIFIED,
    UNKNOWN
}

/**
 * A single immutable result returned by the offline AI guidance module.
 *
 * This data class is the contract between the AI layer and the Android UI.
 * When the AI module is ready, it will construct an [AiResult] and pass it
 * to [AskLifeLineScreen] — the screen itself requires no changes.
 *
 * For now the screen is driven by [AiMockData].
 *
 * @param protocolId      Machine-readable identifier for the matched protocol
 *                        (e.g. "CPR_ADULT_2020"). Empty string when UNKNOWN.
 * @param title           Human-readable protocol name or category
 *                        (e.g. "Adult CPR — Hands-Only").
 * @param confidenceLevel A qualitative label — "HIGH" or "LOW" — derived by the
 *                        AI module from its internal similarity score.
 *                        We deliberately avoid raw floats in the UI to prevent
 *                        false precision (requirement: no fake percentages).
 * @param status          [AiResultStatus.VERIFIED] or [AiResultStatus.UNKNOWN].
 * @param response        The guidance text to display when VERIFIED. May be empty
 *                        when UNKNOWN.
 * @param source          Optional reference or attribution for the protocol
 *                        (e.g. "AHA Guidelines 2020"). Empty string if not available.
 */
data class AiResult(
    val protocolId:      String,
    val title:           String,
    val confidenceLevel: String,   // "HIGH" | "LOW"  — label only, never a raw float
    val status:          AiResultStatus,
    val response:        String,
    val source:          String = ""
)
