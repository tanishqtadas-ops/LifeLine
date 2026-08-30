package com.example.lifeline

/**
 * LOCAL MOCK DATA — for UI development and testing only.
 *
 * These objects stand in for the real AiResult that will eventually be produced
 * by the offline AI guidance module (STT → Semantic Retrieval → Knowledge Base).
 *
 * ─────────────────────────────────────────────────────────────────
 * HOW TO SWITCH BETWEEN VERIFIED AND UNKNOWN IN THE UI:
 *   In AskLifeLineScreen.kt, change the `currentMock` variable:
 *
 *     var currentMock by remember { mutableStateOf(AiMockData.verifiedResult) }
 *     // or
 *     var currentMock by remember { mutableStateOf(AiMockData.unknownResult) }
 *
 *   The toggle button on the screen also switches between them at runtime.
 * ─────────────────────────────────────────────────────────────────
 *
 * When the AI module is integrated, delete or ignore this file and pass the
 * real AiResult directly to AskLifeLineScreen.
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
