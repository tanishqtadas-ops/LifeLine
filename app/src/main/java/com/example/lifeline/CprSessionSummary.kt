package com.example.lifeline

/**
 * Immutable snapshot of a completed CPR training session.
 *
 * Created when the user taps Stop and passed to the SESSION COMPLETE screen.
 * Not persisted to any database — lives only for the duration of the current
 * app session.
 *
 * @param durationSeconds   Total elapsed time of the session in seconds.
 * @param compressionCount  Total confirmed compressions detected by [CprProcessor].
 * @param finalBpm          Last calculated BPM at the moment Stop was tapped.
 *                          0f means the window was not yet full (fewer than 2 compressions).
 * @param finalStatus       Last [CprStatus] at the moment Stop was tapped.
 */
data class CprSessionSummary(
    val durationSeconds: Long,
    val compressionCount: Int,
    val finalBpm: Float,
    val finalStatus: CprStatus
)
