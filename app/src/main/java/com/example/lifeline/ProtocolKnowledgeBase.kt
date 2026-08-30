package com.example.lifeline

/**
 * A single emergency protocol record in the LifeLine knowledge base.
 *
 * SCOPE: Adult (signs of puberty and older) CPR & First Aid only.
 * Pediatric resuscitation (infants <1 yr, children 1 yr to puberty) is explicitly outside Phase 1 scope.
 *
 * @param protocolId     Unique identifier for this protocol.
 * @param title          Short human-readable title.
 * @param description    Situation this protocol covers.
 * @param examplePhrases Natural-language queries that trigger this protocol.
 * @param response       Authoritative guidance text (or placeholder if pending review).
 * @param reference      Official source citation and URL.
 * @param scope          Target clinical population.
 * @param version        Schema/content version.
 */
data class LifeLineProtocol(
    val protocolId: String,
    val title: String,
    val description: String,
    val examplePhrases: List<String>,
    val response: String,
    val reference: String,
    val scope: String = "Adult (signs of puberty and older)",
    val version: Int = 1
)

/**
 * The result of a semantic protocol retrieval.
 *
 * @param protocol       The best-matching protocol record.
 * @param score          Cosine similarity score for the best match (0.0 – 1.0).
 * @param runnerUpId     Protocol ID of the second-best match.
 * @param runnerUpScore  Cosine similarity score of the second-best match.
 */
data class ProtocolMatch(
    val protocol: LifeLineProtocol,
    val score: Float,
    val runnerUpId: String,
    val runnerUpScore: Float
)

/**
 * Phase 1 LifeLine protocol knowledge base.
 *
 * Sourced from the 2025 American Heart Association (AHA) Guidelines for CPR and ECC (Adult Basic Life Support)
 * and AHA/American Red Cross First Aid Guidelines.
 *
 * Target Population: Adults (individuals with signs of puberty and older).
 */
object ProtocolKnowledgeBase {

    val protocols: List<LifeLineProtocol> = listOf(

        LifeLineProtocol(
            protocolId  = "CPR_VOMITING",
            title       = "Vomiting During CPR",
            description = "The patient vomits or regurgitates during CPR compressions.",
            examplePhrases = listOf(
                "The person vomited during CPR.",
                "The patient threw up while I was doing compressions.",
                "They started vomiting in the middle of resuscitation.",
                "The person is regurgitating during chest compressions.",
                "Someone vomited while I was giving CPR."
            ),
            response  = "[RESPONSE PENDING AUTHORITATIVE REVIEW]",
            reference = "Awaiting Authorized Clinical Review: Specialized protocol for bystander management of airway regurgitation during active CPR",
            scope     = "Adult (signs of puberty and older)",
            version   = 1
        ),

        LifeLineProtocol(
            protocolId  = "CPR_RATE",
            title       = "CPR Compression Rate",
            description = "Query about how fast or at what rate to perform CPR chest compressions.",
            examplePhrases = listOf(
                "How fast should CPR compressions be?",
                "How many times per minute should I push during CPR?",
                "What is the correct rate for chest compressions?",
                "How quickly should I do CPR?",
                "What is the recommended CPR speed?",
                "How fast should I push on the chest?"
            ),
            response  = "Push hard and fast in the center of the chest at a rate of 100 to 120 compressions per minute. Compress to a depth of at least 2 inches (5 cm) and avoid excessive depth greater than 2.4 inches (6 cm). Allow the chest to fully recoil after each push.",
            reference = "American Heart Association (AHA) — 2025 American Heart Association Guidelines for CPR and ECC — Adult Basic Life Support (https://cpr.heart.org/en/resuscitation-science/cpr-and-ecc-guidelines/adult-basic-life-support)",
            scope     = "Adult (signs of puberty and older)",
            version   = 1
        ),

        LifeLineProtocol(
            protocolId  = "CPR_NOT_RESPONDING",
            title       = "Patient Not Responding",
            description = "The patient is unresponsive and does not react to stimulation.",
            examplePhrases = listOf(
                "The person is not responding.",
                "They are unresponsive.",
                "The patient does not react to anything.",
                "I cannot wake them up.",
                "They are unconscious and not moving.",
                "The person collapsed and is not waking up."
            ),
            response  = "Tap the person's shoulders firmly and shout to check responsiveness. Check for breathing for no more than 10 seconds. If the person is unresponsive and not breathing normally (or only gasping), activate your local emergency response system immediately and begin chest compressions.",
            reference = "American Heart Association (AHA) — 2025 American Heart Association Guidelines for CPR and ECC — Adult Basic Life Support (https://cpr.heart.org/en/resuscitation-science/cpr-and-ecc-guidelines/adult-basic-life-support)",
            scope     = "Adult (signs of puberty and older)",
            version   = 1
        ),

        LifeLineProtocol(
            protocolId  = "CPR_BREATHING",
            title       = "Patient Is Breathing",
            description = "The patient shows signs of breathing during or after resuscitation.",
            examplePhrases = listOf(
                "The person is breathing.",
                "They started breathing again.",
                "I can see the chest rising and falling.",
                "The patient is breathing on their own.",
                "They seem to be breathing now.",
                "I can feel breath on my hand."
            ),
            response  = "If the person is unresponsive or not fully awake but is breathing normally, place them on their side in a recovery position (if no trauma is suspected) to keep the airway open. Continuously monitor their breathing. If normal breathing stops, roll them onto their back and begin CPR immediately.",
            reference = "American Heart Association (AHA) & American Red Cross (ARC) — Focused Update on First Aid: Recovery Position for the Unresponsive Breathing Individual (2024/2025) (https://www.redcross.org/take-a-class/first-aid/performing-first-aid/recovery-position)",
            scope     = "Adult (signs of puberty and older)",
            version   = 1
        ),

        LifeLineProtocol(
            protocolId  = "CPR_START",
            title       = "How to Start CPR",
            description = "The bystander needs instructions on how to begin CPR.",
            examplePhrases = listOf(
                "How do I start CPR?",
                "What do I do to begin CPR?",
                "How do I perform CPR?",
                "Tell me how to do CPR.",
                "I need to start CPR, what do I do?",
                "Can you guide me through CPR?",
                "How do I give chest compressions?"
            ),
            response  = "1. Assess scene safety and check responsiveness. 2. If the person is unresponsive and not breathing normally (or only gasping), activate your local emergency response system and get an AED if available. 3. Place your hands in the center of the chest and begin chest compressions immediately at a rate of 100 to 120 compressions per minute.",
            reference = "American Heart Association (AHA) — 2025 American Heart Association Guidelines for CPR and ECC — Adult Basic Life Support (https://cpr.heart.org/en/resuscitation-science/cpr-and-ecc-guidelines/adult-basic-life-support)",
            scope     = "Adult (signs of puberty and older)",
            version   = 1
        ),

        LifeLineProtocol(
            protocolId  = "WAITING_FOR_HELP",
            title       = "Waiting for Emergency Services",
            description = "The bystander is waiting for an ambulance and needs guidance on what to do.",
            examplePhrases = listOf(
                "What should I do while waiting for emergency help?",
                "The ambulance is on the way, what should I do?",
                "I called 911, what do I do now?",
                "Help is coming, what should I do in the meantime?",
                "I am waiting for the paramedics, what can I do?",
                "Emergency services are coming, what should I do?"
            ),
            response  = "Stay on speakerphone and follow emergency dispatcher instructions. Continue chest compressions at 100 to 120 compressions per minute, and when an AED arrives, apply it and follow its prompts. Continue CPR/AED care as directed until emergency responders take over or the dispatcher/AED instructs otherwise.",
            reference = "American Heart Association (AHA) — 2025 American Heart Association Guidelines for CPR and ECC — Adult Basic Life Support (https://cpr.heart.org/en/resuscitation-science/cpr-and-ecc-guidelines/adult-basic-life-support)",
            scope     = "Adult (signs of puberty and older)",
            version   = 1
        )
    )
}
