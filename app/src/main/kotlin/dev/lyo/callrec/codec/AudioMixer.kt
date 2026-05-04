// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.codec

import com.coolappstore.evercallrecorder.by.svhp.core.L
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Two-output mixer for the dual-stream call recordings:
 *
 *  - [mixToStereoWav] — soft-pan (≈75/25) stereo for human listening (sharing,
 *    cached playback). Uplink leans left, downlink leans right, each still
 *    audible in the other ear so that pulling one earbud doesn't lose half the
 *    conversation.
 *
 *  - [mixNormalizedMonoForStt] — RMS-normalised mono sum for cloud audio LLM
 *    transcription. Gemini's official docs (ai.google.dev/gemini-api/docs/audio)
 *    state multi-channel audio is automatically combined into a single channel
 *    before processing, so any panning is wasted bytes. The real diarization
 *    bug is per-side level imbalance: uplink (mic-direct) is typically ~12 dB
 *    louder than downlink (post-codec / acoustic loop), so the naive sum lets
 *    the louder side dominate and the model only "hears" the user. We level
 *    each side to a common target RMS before summing.
 *
 * Decoding is delegated to [PcmDecoder] — single source of truth for PCM
 * extraction; this object is purely the mix + RIFF-write step.
 */
object AudioMixer {

    private const val SAMPLE_RATE = 16_000

    private const val DOMINANT = 0.75f
    private const val BLEED = 1f - DOMINANT

    /**
     * Decode both files to int16 PCM mono, soft-pan into stereo, write WAV.
     * Returns the output file on success, null if either decode fails.
     */
    fun mixToStereoWav(uplink: File, downlink: File, out: File): File? {
        val upPcm = PcmDecoder.readBytes(uplink) ?: return null
        val dnPcm = PcmDecoder.readBytes(downlink) ?: return null
        val frames = min(upPcm.size, dnPcm.size) / 2 // bytes per int16
        val stereo = ByteArray(frames * 4) // 2 channels × 2 bytes
        val upBuf = ByteBuffer.wrap(upPcm).order(ByteOrder.LITTLE_ENDIAN)
        val dnBuf = ByteBuffer.wrap(dnPcm).order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = ByteBuffer.wrap(stereo).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) {
            val u = upBuf.short.toInt()
            val d = dnBuf.short.toInt()
            // L: dominant uplink + bleed downlink. R: mirror.
            // Sum of weights = 1.0 → no clipping for valid int16 inputs.
            val l = (u * DOMINANT + d * BLEED).toInt().coerceIn(-32768, 32767)
            val r = (u * BLEED + d * DOMINANT).toInt().coerceIn(-32768, 32767)
            outBuf.putShort(l.toShort())
            outBuf.putShort(r.toShort())
        }
        writeWav(out, SAMPLE_RATE, channels = 2, pcm = stereo)
        L.i(TAG, "soft-pan mix → ${out.path} (${stereo.size} bytes, $frames frames)")
        return out
    }

    /**
     * Decode both files, level-match each side to [TARGET_RMS], sum to mono
     * int16, write a single-channel 16 kHz WAV. This is the file we feed to
     * cloud STT so each speaker has comparable presence in the final mono
     * spectrogram regardless of which side the recorder captured louder.
     *
     * The [MIN_RMS_FOR_GAIN] floor and [MAX_GAIN] ceiling prevent two
     * pathologies: amplifying near-silent sides into pure noise, and blowing
     * up a barely-audible side by 30+ dB when the other party never spoke
     * loud enough to register.
     */
    fun mixNormalizedMonoForStt(uplink: File, downlink: File, out: File): File? {
        val upPcm = PcmDecoder.readBytes(uplink) ?: return null
        val dnPcm = PcmDecoder.readBytes(downlink) ?: return null
        val upRms = rmsOf(upPcm)
        val dnRms = rmsOf(dnPcm)
        val upGain = gainFor(upRms)
        val dnGain = gainFor(dnRms)

        val frames = min(upPcm.size, dnPcm.size) / 2
        val mono = ByteArray(frames * 2)
        val upBuf = ByteBuffer.wrap(upPcm).order(ByteOrder.LITTLE_ENDIAN)
        val dnBuf = ByteBuffer.wrap(dnPcm).order(ByteOrder.LITTLE_ENDIAN)
        val outBuf = ByteBuffer.wrap(mono).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) {
            val u = upBuf.short.toDouble() * upGain
            val d = dnBuf.short.toDouble() * dnGain
            val s = (u + d).toInt().coerceIn(-32768, 32767)
            outBuf.putShort(s.toShort())
        }
        writeWav(out, SAMPLE_RATE, channels = 1, pcm = mono)
        L.i(
            TAG,
            "stt-mix → ${out.path} frames=$frames " +
                "upRms=${upRms.toInt()}×%.2f dnRms=${dnRms.toInt()}×%.2f"
                    .format(upGain, dnGain),
        )
        return out
    }

    private fun rmsOf(pcm: ByteArray): Double {
        val buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        var n = 0L
        while (buf.remaining() >= 2) {
            val s = buf.short.toDouble()
            sumSq += s * s
            n++
        }
        return if (n == 0L) 0.0 else sqrt(sumSq / n)
    }

    private fun gainFor(rms: Double): Double =
        if (rms < MIN_RMS_FOR_GAIN) 1.0
        else (TARGET_RMS / rms).coerceAtMost(MAX_GAIN)

    private fun writeWav(file: File, sampleRate: Int, channels: Int, pcm: ByteArray) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val bitsPerSample = 16
            val blockAlign = channels * bitsPerSample / 8
            val byteRate = sampleRate * blockAlign
            val totalSize = 36 + pcm.size
            val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(totalSize); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1)
                putShort(channels.toShort()); putInt(sampleRate); putInt(byteRate)
                putShort(blockAlign.toShort()); putShort(bitsPerSample.toShort())
                put("data".toByteArray()); putInt(pcm.size)
            }.array()
            raf.write(hdr); raf.write(pcm)
        }
    }

    // ~−18 dBFS in int16 (32767 × 10^(-18/20)). Conservative target: two
    // signals normalised here sum to ≈ −15 dBFS RMS, leaving ~15 dB of
    // headroom for transient peaks before int16 clipping kicks in.
    private const val TARGET_RMS = 4128.0
    private const val MIN_RMS_FOR_GAIN = 50.0
    private const val MAX_GAIN = 8.0

    private const val TAG = "Mixer"
}
