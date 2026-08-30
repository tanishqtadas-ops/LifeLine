package com.example.lifeline

/**
 * LOCAL MOCK DATA — for UI development and testing only.
 *
 * These objects stand in for the real AiResult that will eventually be produced
 * by the offline AI guidance module (STT → Semantic Retrieval → Knowledge Base).
 *
 * ─────────────────────────────────────────────────────────────────
 * HOW TO SWITCH BETWEEN VERIFIED AND UNKNOWN IN THE UI:
 *   The dev toggle lives in AppNavHost (MainActivity.kt) — tap the banner
 *   at the top of the Ask LifeLine tab at runtime.
 *
 *   To change the starting state, edit `showVerifiedMock` in AppNavHost:
 *     var showVerifiedMock by remember { mutableStateOf(false) }  // starts UNKNOWN
 * ─────────────────────────────────────────────────────────────────
 *
 * When the AI module is integrated:
 *   1. Remove the DevMockSelector composable from AppNavHost.
 *   2. Replace `currentAiResult` with the real result from the AI engine.
 *   3. This file (AiMockData.kt) can then be deleted.
 */
object AiMockData {

    /**
     * MOCK: HIGH-confidence VERIFIED result.
     * Simulates the AI module successfully matching a user query such as
     * "How do I do hands-only CPR?" to a local protocol.
     */
    val verifiedResult = AiResult(
        protocolId      = "CPR_ADULT_HANDS_ONLY_2020",
        title           = "Adult CPR — Hands-Only",
        confidenceLevel = "HIGH",
        status          = AiResultStatus.VERIFIED,
        response        = """
            1. Call emergency services immediately (112 / 108).
            2. Place the heel of your hand on the centre of the person's chest.
            3. Place your other hand on top and interlock your fingers.
            4. Press down firmly at least 5 cm (2 inches) at a rate of
               100–120 compressions per minute.
            5. Do not stop until emergency responders arrive or the person
               begins to breathe normally.
        """.trimIndent(),
        source          = "AHA Guidelines 2020 · BLS for Healthcare Providers"
    )

    /**
     * MOCK: LOW-confidence UNKNOWN result.
     * Simulates the AI module failing to match the query to any protocol with
     * sufficient confidence — for example, a query outside the knowledge base.
     */
    val unknownResult = AiResult(
        protocolId      = "",
        title           = "Query Not Recognised",
        confidenceLevel = "LOW",
        status          = AiResultStatus.UNKNOWN,
        response        = "",
        source          = ""
    )
}
