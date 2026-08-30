package com.example.lifeline

import kotlin.math.sqrt

/**
 * CprProcessor — CPR compression detection and BPM estimation.
 *
 * Signal pipeline (in order):
 *   1. Compute total acceleration magnitude  √(x²+y²+z²)
 *      → orientation-agnostic; works regardless of how the phone is held.
 *   2. Remove gravity with an exponential moving average (EMA).
 *      The EMA tracks the slow-moving gravity component (α = 0.8 = heavy bias
 *      toward the past) and we subtract it, leaving only dynamic motion signal.
 *   3. Smooth the dynamic signal with a gentler EMA (α = 0.15) to reduce
 *      high-frequency sensor jitter without adding significant lag.
 *   4. Detect peaks: a compression is *candidate* when the smoothed signal
 *      rises above PEAK_THRESHOLD_MS2. It is *confirmed* when the signal
 *      subsequently drops back below RESET_THRESHOLD_MS2 (hysteresis),
 *      preventing the same peak from being counted twice.
 *   5. Enforce a refractory period of MIN_COMPRESSION_INTERVAL_MS after each
 *      confirmed compression — hard physiological ceiling (~200 BPM max).
 *   6. Maintain a circular buffer of the last WINDOW_SIZE compression
 *      timestamps. BPM = (WINDOW_SIZE − 1) / timespan_of_window_in_minutes.
 *      Fewer than 2 events → BPM is reported as 0.
 *
 * Assumptions:
 *   - Phone is held against the chest / placed on a CPR manikin during training.
 *   - Each compression produces one distinct acceleration impulse above the
 *     threshold.
 *   - Sensor delivers samples at ~50 Hz (SENSOR_DELAY_GAME).
 *
 * Limitations:
 *   - Threshold is heuristic (1.5 m/s²); real-world tuning may be needed.
 *   - BPM lags ~4–5 seconds at session start (window fills gradually).
 *   - Accuracy degrades if the phone is moved or repositioned during a session.
 *   - Training/prototype use only — not a medical device.
 */
class CprProcessor {

    // ── Tuneable constants ────────────────────────────────────────────────────

    /** A peak (compression) must exceed this dynamic acceleration (m/s²). */
    private val PEAK_THRESHOLD_MS2 = 1.5f

    /**
     * Signal must fall back below this level before the next peak is eligible.
     * Provides hysteresis to prevent a single broad peak from being counted twice.
     */
    private val RESET_THRESHOLD_MS2 = PEAK_THRESHOLD_MS2 / 2f

    /**
     * Minimum milliseconds between two confirmed compressions.
     * 300 ms ≈ 200 BPM ceiling — well above the AHA target range.
     */
    private val MIN_COMPRESSION_INTERVAL_MS = 300L

    /** Number of recent compression timestamps kept for BPM smoothing. */
    private val WINDOW_SIZE = 8

    /** α for gravity EMA — high value = slow adaptation = good gravity tracker. */
    private val GRAVITY_ALPHA = 0.8f

    /** α for signal smoothing EMA — low value = heavy smoothing. */
    private val SMOOTH_ALPHA = 0.15f

    /** CPR target range (AHA guidelines). */
    private val BPM_MIN = 100f
    private val BPM_MAX = 120f

    // ── Runtime state ─────────────────────────────────────────────────────────

    private var gravityEstimate = 0f       // Running EMA of acceleration magnitude
    private var smoothedSignal = 0f        // EMA of gravity-removed dynamic signal
    private var isAboveThreshold = false   // True while signal is over PEAK_THRESHOLD
    private var lastCompressionTimeMs = 0L // Wall-clock ms of last confirmed compression

    /** Circular buffer of the last WINDOW_SIZE compression wall-clock timestamps (ms). */
    private val compressionTimestamps = ArrayDeque<Long>(WINDOW_SIZE + 1)

    // ── Public read-only state ────────────────────────────────────────────────

    /** Total confirmed compressions since last reset(). */
    var compressionCount: Int = 0
        private set

    /** Estimated compressions per minute, derived from the sliding window. 0 if < 2 events. */
    var bpm: Float = 0f
        private set

    /** Human-readable training status based on current BPM. */
    var status: CprStatus = CprStatus.IDLE
        private set

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Reset all state. Call this at the start of every new training session.
     */
    fun reset() {
        gravityEstimate = 0f
        smoothedSignal = 0f
        isAboveThreshold = false
        lastCompressionTimeMs = 0L
        compressionTimestamps.clear()
        compressionCount = 0
        bpm = 0f
        status = CprStatus.IDLE
    }

    /**
     * Feed a raw accelerometer sample into the pipeline.
     *
     * @param x  Acceleration on X axis (m/s²)
     * @param y  Acceleration on Y axis (m/s²)
     * @param z  Acceleration on Z axis (m/s²)
     * @param timestampNs  Sensor event timestamp in nanoseconds (from SensorEvent.timestamp).
     *                     Used only for ordering; wall-clock time (System.currentTimeMillis)
     *                     drives BPM so the sliding window is stable even if the sensor
     *                     clock drifts relative to real time.
     */
    fun processSample(x: Float, y: Float, z: Float, @Suppress("UNUSED_PARAMETER") timestampNs: Long) {

        // ── Step 1: Acceleration magnitude (orientation-agnostic) ─────────────
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        // ── Step 2: Gravity removal via EMA ──────────────────────────────────
        // On the very first call gravityEstimate is 0; warm it up immediately.
        if (gravityEstimate == 0f) {
            gravityEstimate = magnitude
        }
        gravityEstimate = GRAVITY_ALPHA * gravityEstimate + (1f - GRAVITY_ALPHA) * magnitude
        val dynamicAccel = magnitude - gravityEstimate

        // ── Step 3: Smooth the dynamic signal ────────────────────────────────
        smoothedSignal = SMOOTH_ALPHA * dynamicAccel + (1f - SMOOTH_ALPHA) * smoothedSignal

        // ── Step 4 & 5: Peak detection with hysteresis + refractory period ───
        val nowMs = System.currentTimeMillis()

        if (!isAboveThreshold) {
            // Signal just crossed UP through the peak threshold → candidate peak
            if (smoothedSignal >= PEAK_THRESHOLD_MS2) {
                isAboveThreshold = true

                // ── Refractory check ──────────────────────────────────────────
                val elapsedSinceLast = nowMs - lastCompressionTimeMs
                if (lastCompressionTimeMs == 0L || elapsedSinceLast >= MIN_COMPRESSION_INTERVAL_MS) {
                    // ── Confirm compression ───────────────────────────────────
                    compressionCount++
                    lastCompressionTimeMs = nowMs
                    recordTimestamp(nowMs)
                    updateBpm()
                }
            }
        } else {
            // Signal must fall back below the reset threshold before the next peak
            if (smoothedSignal < RESET_THRESHOLD_MS2) {
                isAboveThreshold = false
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Add a timestamp to the sliding window, evicting the oldest if full. */
    private fun recordTimestamp(nowMs: Long) {
        compressionTimestamps.addLast(nowMs)
        while (compressionTimestamps.size > WINDOW_SIZE) {
            compressionTimestamps.removeFirst()
        }
    }

    /**
     * Recalculate BPM from the sliding window and update [status].
     * BPM = (N − 1) / duration_in_minutes, where N = window size.
     */
    private fun updateBpm() {
        val n = compressionTimestamps.size
        if (n < 2) {
            bpm = 0f
            status = CprStatus.IDLE
            return
        }

        val spanMs = compressionTimestamps.last() - compressionTimestamps.first()
        if (spanMs <= 0L) return  // Guard against identical timestamps (shouldn't happen)

        val spanMinutes = spanMs / 60_000f
        bpm = (n - 1).toFloat() / spanMinutes

        status = when {
            bpm < BPM_MIN  -> CprStatus.TOO_SLOW
            bpm > BPM_MAX  -> CprStatus.TOO_FAST
            else           -> CprStatus.GOOD_RHYTHM
        }
    }
}

/**
 * Training feedback status derived from the current BPM reading.
 *
 * IDLE        — fewer than 2 compressions; BPM not yet meaningful
 * TOO_SLOW    — BPM < 100 (below AHA target)
 * GOOD_RHYTHM — 100 ≤ BPM ≤ 120 (within AHA target)
 * TOO_FAST    — BPM > 120 (above AHA target)
 */
enum class CprStatus {
    IDLE,
    TOO_SLOW,
    GOOD_RHYTHM,
    TOO_FAST
}
