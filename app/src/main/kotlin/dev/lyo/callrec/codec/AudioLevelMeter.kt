// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.codec

import kotlin.math.sqrt

/**
 * Running RMS over 16-bit little-endian PCM. Cheap; runs on every pump tick.
 * Drives the live UI meter and the controller's audibility decision via an
 * adaptive noise floor — the first ~500 ms of audio (by **frame count**, not
 * by call count) set [calibratedFloor], and [isAudible] is true iff the
 * latest RMS is meaningfully above floor.
 *
 * RMS is normalised to int16 full-scale → returns Float in [0.0, 1.0].
 *
 * NOTE on warmup timing: the previous implementation gated warmup on `update()`
 * call count — but the recorder pump uses ~8 KB reads (≈500 ms of 16 kHz mono
 * audio per call), so 50 calls = ~25 s of audio, not ~500 ms. That meant the
 * floor often included voice (Pixel ringback at ~1 s) → calibratedFloor was
 * too high → real conversation registered as silence → strategy fell through
 * the ladder unnecessarily. Fixed by gating on accumulated frames against
 * `sampleRate / 2`, which is genuinely 500 ms regardless of buffer size.
 */
class AudioLevelMeter(private val sampleRate: Int = 16_000) {

    @Volatile var lastRms: Float = 0f
        private set
    @Volatile var maxRms: Float = 0f
        private set
    @Volatile var totalFrames: Long = 0L
        private set
    @Volatile var silentFrames: Long = 0L
        private set
    /** Median RMS of the warmup window. INITIAL_FLOOR until warmup completes. */
    @Volatile var calibratedFloor: Float = INITIAL_FLOOR
        private set

    private val warmupFrameBudget: Long = (sampleRate / 2).toLong() // 500 ms
    private val warmupSamples = ArrayList<Float>(16)
    private var warmupComplete = false

    /** True when the latest RMS sample exceeds the calibrated noise floor by [AUDIBLE_DELTA]. */
    val isAudible: Boolean get() = lastRms > calibratedFloor + AUDIBLE_DELTA

    fun update(buf: ByteArray, off: Int, len: Int) {
        if (len < 2) return
        val frames = len / 2
        var sumSq = 0.0
        var i = off
        val end = off + len
        while (i < end) {
            val low = buf[i].toInt() and 0xFF
            val high = buf[i + 1].toInt()
            val sample = (high shl 8) or low
            val v = sample.toDouble()
            sumSq += v * v
            i += 2
        }
        val rms = (sqrt(sumSq / frames) / FULL_SCALE).toFloat().coerceIn(0f, 1f)
        lastRms = rms
        if (rms > maxRms) maxRms = rms
        totalFrames += frames
        if (rms < SILENCE_FLOOR) silentFrames += frames else silentFrames = 0

        if (!warmupComplete) {
            warmupSamples += rms
            // Lock the floor as soon as we've accumulated enough audio time —
            // not enough call count. Guarantees ~500 ms of real audio is in
            // the window regardless of pump buffer size.
            if (totalFrames >= warmupFrameBudget && warmupSamples.isNotEmpty()) {
                val sorted = warmupSamples.sorted()
                calibratedFloor = sorted[sorted.size / 2].coerceAtLeast(INITIAL_FLOOR)
                warmupComplete = true
            }
        }
    }

    fun reset() {
        lastRms = 0f
        maxRms = 0f
        totalFrames = 0L
        silentFrames = 0L
        calibratedFloor = INITIAL_FLOOR
        warmupSamples.clear()
        warmupComplete = false
    }

    fun isSilent(sampleRate: Int = this.sampleRate, windowMs: Long = 2_000): Boolean =
        silentFrames * 1_000L / sampleRate >= windowMs

    companion object {
        private const val FULL_SCALE = 32_768.0
        /** -60 dBFS — quieter than ambient room noise. */
        private const val SILENCE_FLOOR = 0.001f
        /** Bootstrap floor before warmup completes. */
        private const val INITIAL_FLOOR = 0.001f
        /** ~+6 dB above floor — empirical voice-vs-drift discriminator. */
        private const val AUDIBLE_DELTA = 0.008f
    }
}
