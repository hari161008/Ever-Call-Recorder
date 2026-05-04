// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.codec

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioLevelMeterTest {

    @Test fun `silent buffer yields rms near zero`() {
        val m = AudioLevelMeter()
        m.update(ByteArray(2_048), 0, 2_048)
        assertThat(m.lastRms).isLessThan(0.001f)
        assertThat(m.silentFrames).isEqualTo(1_024)
    }

    @Test fun `audible sine wave yields nontrivial rms`() {
        val m = AudioLevelMeter()
        // 1 second of 1 kHz sine at 16 kHz, half-amplitude.
        val sr = 16_000
        val frames = sr
        val buf = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val v = (sin(2 * PI * 1_000 * i / sr) * 16_000).toInt().toShort()
            buf[i * 2] = (v.toInt() and 0xFF).toByte()
            buf[i * 2 + 1] = ((v.toInt() ushr 8) and 0xFF).toByte()
        }
        m.update(buf, 0, buf.size)
        // RMS of half-amplitude sine = (16000 / 32768) / sqrt(2) ≈ 0.345.
        assertThat(m.lastRms).isWithin(0.05f).of(0.345f)
        assertThat(m.silentFrames).isEqualTo(0)
    }

    @Test fun `silentFrames resets when audible sample arrives`() {
        val m = AudioLevelMeter()
        m.update(ByteArray(1_000), 0, 1_000)        // 500 frames of silence
        assertThat(m.silentFrames).isEqualTo(500)

        // Now a chunk with one loud sample at the end.
        val mixed = ByteArray(1_000)
        // last sample = 0x7FFF (max positive int16), little endian
        mixed[mixed.size - 2] = 0xFF.toByte()
        mixed[mixed.size - 1] = 0x7F
        m.update(mixed, 0, mixed.size)
        assertThat(m.silentFrames).isEqualTo(0)
    }

    @Test fun `isSilent uses sample-rate aware window`() {
        val m = AudioLevelMeter()
        // 16 kHz, 2 s of silence = 32 000 frames.
        m.update(ByteArray(64_000), 0, 64_000)
        assertThat(m.isSilent(sampleRate = 16_000, windowMs = 2_000)).isTrue()
        assertThat(m.isSilent(sampleRate = 16_000, windowMs = 5_000)).isFalse()
    }

    private fun rmsToBytes(rms: Float, sampleCount: Int = 256): ByteArray {
        val amplitude = (rms * 32_768f).toInt().coerceIn(0, 32_767).toShort()
        val out = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val v = if (i % 2 == 0) amplitude else (-amplitude.toInt()).toShort()
            out[i * 2] = (v.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun calibratedFloor_convergesToMedianOverWarmup() {
        val meter = AudioLevelMeter()
        repeat(50) { meter.update(rmsToBytes(0.003f), 0, 512) }
        meter.update(rmsToBytes(0.003f), 0, 512)
        assertThat(meter.calibratedFloor).isWithin(0.0005f).of(0.003f)
    }

    @Test
    fun isAudible_falseDuringWarmup() {
        val meter = AudioLevelMeter()
        meter.update(rmsToBytes(0.05f), 0, 512)
        // Before warmup completes, calibratedFloor=INITIAL_FLOOR=0.001, AUDIBLE_DELTA=0.008
        // → threshold 0.009. 0.05 RMS exceeds → audible=true.
        assertThat(meter.isAudible).isTrue()
    }

    @Test
    fun isAudible_falseAtFloor_trueAboveDelta() {
        val meter = AudioLevelMeter()
        repeat(51) { meter.update(rmsToBytes(0.01f), 0, 512) }
        // calibratedFloor ≈ 0.01, threshold = 0.018
        meter.update(rmsToBytes(0.015f), 0, 512)
        assertThat(meter.isAudible).isFalse()
        meter.update(rmsToBytes(0.025f), 0, 512)
        assertThat(meter.isAudible).isTrue()
    }

    @Test
    fun reset_clearsWarmupAndFloor() {
        val meter = AudioLevelMeter()
        repeat(51) { meter.update(rmsToBytes(0.05f), 0, 512) }
        assertThat(meter.calibratedFloor).isGreaterThan(0.001f)
        meter.reset()
        assertThat(meter.calibratedFloor).isWithin(0.00001f).of(0.001f)
    }
}
