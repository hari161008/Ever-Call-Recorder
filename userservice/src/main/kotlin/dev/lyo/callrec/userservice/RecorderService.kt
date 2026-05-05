// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.userservice

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import com.coolappstore.evercallrecorder.by.svhp.aidl.IRecorderService
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Privileged recorder. Lives in the Shizuku-spawned `app_process` (UID 2000 =
 * shell). Reachable via Binder from the app process through Shizuku's
 * UserService API.
 *
 * Two pumps run in parallel for the dual-track strategy. The Stub itself is
 * stateful but lock-free for the hot path (AtomicInteger state + per-job
 * thread-local lifetime).
 */
class RecorderService : IRecorderService.Stub() {

    init {
        Log.i(TAG, "RecorderService init begin (uid=${Process.myUid()})")
        HiddenApiBootstrap.enable()
        // Stub is instantiated on Shizuku's main thread (which has a Looper).
        // Prime the system Context cache here so later AIDL transactions —
        // which run on Binder threads with NO Looper — don't have to call
        // `ActivityThread.systemMain()` themselves (it'd throw on Binder).
        ServiceContext.primeFromMainThread()
    }

    /**
     * Context that AudioRecord ctor / AppOps see. For shell UID this is a
     * [WrappedShellContext] (AppOps check chains through this for the
     * RECORD_AUDIO permission); for root UID the system Context is enough.
     *
     * Eagerly built (not lazy) so reflection runs on the construction thread,
     * not on the first Binder transaction. Wrapped in runCatching so
     * reflection failures don't take down the whole Stub — better to record
     * with raw context (which may still work on some Android versions) than
     * to refuse to bind at all.
     */
    private val captureContext: Context = run {
        val raw = ServiceContext.get()
        if (Process.myUid() == 0) {
            raw
        } else {
            runCatching { WrappedShellContext(raw) }
                .onFailure { Log.e(TAG, "WrappedShellContext failed; falling back to raw context", it) }
                .getOrDefault(raw)
        }
    }.also { Log.i(TAG, "captureContext ready: ${it.javaClass.simpleName} pkg=${it.opPackageName}") }

    private val state = AtomicInteger(STATE_IDLE)
    @Volatile private var uplinkJob: AudioRecorderJob? = null
    @Volatile private var downlinkJob: AudioRecorderJob? = null
    private val lastError = AtomicReference<String?>(null)

    override fun getVersion(): Int = BuildConfig.VERSION_CODE_USERSERVICE

    // Read-only diagnostic; no verifyCaller() — health value is not sensitive
    // and gating it would create a chicken-and-egg with debug-build cert pinning.
    override fun getBypassHealth(): Int = when {
        Process.myUid() == 0 -> 2  // root: no bypass needed → trivially Full
        captureContext is WrappedShellContext -> captureContext.health.ordinal
        else -> 0  // non-root without wrapper = misconfigured = Failed
    }

    override fun startDualRecord(
        uplinkSource: Int,
        downlinkSource: Int,
        sampleRate: Int,
        uplinkFd: ParcelFileDescriptor,
        downlinkFd: ParcelFileDescriptor,
    ): Int {
        Log.i(TAG, "startDualRecord up=$uplinkSource dn=$downlinkSource sr=$sampleRate")
        try {
            verifyCaller()
        } catch (t: Throwable) {
            Log.e(TAG, "startDualRecord verifyCaller threw", t)
            lastError.set("verifyCaller: ${t.message}")
            uplinkFd.close(); downlinkFd.close()
            throw t
        }
        if (!state.compareAndSet(STATE_IDLE, STATE_STARTING)) {
            Log.w(TAG, "startDualRecord rejected — state=${state.get()} not IDLE")
            uplinkFd.close(); downlinkFd.close()
            return 0
        }

        val up = AudioRecorderJob("uplink", uplinkSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, uplinkFd, captureContext)
        val dn = AudioRecorderJob("downlink", downlinkSource, sampleRate, AudioFormat.CHANNEL_IN_MONO, downlinkFd, captureContext)

        val upOk = up.init()
        val dnOk = dn.init()
        Log.i(TAG, "startDualRecord init: up=$upOk(${up.errorMessage}) dn=$dnOk(${dn.errorMessage})")

        if (!upOk && !dnOk) {
            lastError.set(
                "dual init failed: up=${up.errorMessage}; dn=${dn.errorMessage}"
            )
            // Both pipes were ours; jobs that failed init never opened their FDs,
            // close them so the client read-side gets EOF instead of hanging.
            runCatching { uplinkFd.close() }
            runCatching { downlinkFd.close() }
            state.set(STATE_IDLE)
            return 0
        }

        var mask = 0
        if (upOk) { uplinkJob = up; mask = mask or 0b01; up.start() } else { runCatching { uplinkFd.close() } }
        if (dnOk) { downlinkJob = dn; mask = mask or 0b10; dn.start() } else { runCatching { downlinkFd.close() } }

        state.set(STATE_DUAL)
        return mask
    }

    override fun startSingleRecord(
        source: Int,
        sampleRate: Int,
        channelMask: Int,
        pcmFd: ParcelFileDescriptor,
    ): Int {
        Log.i(TAG, "startSingleRecord src=$source sr=$sampleRate ch=$channelMask")
        try {
            verifyCaller()
        } catch (t: Throwable) {
            Log.e(TAG, "startSingleRecord verifyCaller threw", t)
            lastError.set("verifyCaller: ${t.message}")
            pcmFd.close()
            throw t
        }
        if (!state.compareAndSet(STATE_IDLE, STATE_STARTING)) {
            Log.w(TAG, "startSingleRecord rejected — state=${state.get()} not IDLE")
            pcmFd.close()
            return 0
        }

        val job = AudioRecorderJob("single", source, sampleRate, channelMask, pcmFd, captureContext)
        val ok = job.init()
        Log.i(TAG, "startSingleRecord init: ok=$ok err=${job.errorMessage}")
        if (!ok) {
            lastError.set("single init failed: ${job.errorMessage}")
            runCatching { pcmFd.close() }
            state.set(STATE_IDLE)
            return 0
        }
        uplinkJob = job
        job.start()
        state.set(STATE_SINGLE)
        return 1
    }

    override fun stop() {
        verifyCaller()
        // Drop state first so any lingering pump exits its loop on next read.
        state.set(STATE_IDLE)
        uplinkJob?.stop()
        downlinkJob?.stop()
        uplinkJob = null
        downlinkJob = null
    }

    override fun getState(): Int = state.get()

    override fun getLastError(): String? = lastError.get()

    /**
     * Defence in depth: with `daemon=true` the Binder lives across our app's
     * lifetime. Any other Shizuku-permitted package on the device could in
     * theory enumerate Binders and call us. Reject anything that isn't us.
     *
     * Verifies UID → package mapping AND signing certificate SHA-256 against
     * the constant baked into the userservice module at build time.
     */
    private fun verifyCaller() {
        val uid = Binder.getCallingUid()
        if (uid == android.os.Process.myUid()) return // intra-process probe
        val pm = ServiceContext.get().packageManager
        val pkgs = pm.getPackagesForUid(uid)
            ?: throw SecurityException("verifyCaller: no packages for uid=$uid")
        val ourPkg = BuildConfig.APP_PACKAGE_ID
        // Accept the canonical package and any debug/internal-test variant
        // (applicationIdSuffix produces "com.coolappstore.evercallrecorder.by.svhp.debug"). The signing
        // cert pin below — when configured — keeps this safe; without it, the
        // prefix is still tighter than reality, since the only way for a
        // foreign app to inherit our UID is to share signature anyway.
        val matched = pkgs.any { it == ourPkg || it.startsWith("$ourPkg.") }
        if (!matched) throw SecurityException("verifyCaller: foreign uid=$uid pkgs=${pkgs.toList()} expected~$ourPkg")

        // Signing cert pinning.
        val expected = BuildConfig.APP_SIGNING_SHA256
        if (expected.isEmpty()) return // unset in debug builds — fall through

        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(ourPkg, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = info.signingInfo?.apkContentsSigners ?: emptyArray()
        if (signers.isEmpty()) throw SecurityException("verifyCaller: no signing certs")
        val md = MessageDigest.getInstance("SHA-256")
        val signatureOk = signers.any { it.toByteArray().let(md::digest).toHex() == expected }
        if (!signatureOk) throw SecurityException("verifyCaller: signing cert mismatch")
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val u = b.toInt() and 0xFF
            sb.append(HEX[u ushr 4]); sb.append(HEX[u and 0x0F])
        }
        return sb.toString()
    }

    /** Required by Shizuku UserService — onBind result is the Stub itself. */
    @Suppress("unused")
    fun onBind(): IBinder = this

    @Suppress("unused")
    fun onDestroy() {
        runCatching { stop() }
    }

    companion object {
        // logcat tag deliberately mirrors the app-side "Callrec" so a single
        // `adb logcat -s Callrec:V` shows both processes interleaved.
        private const val TAG = "Callrec"

        const val STATE_IDLE = 0
        const val STATE_STARTING = 1
        const val STATE_DUAL = 2
        const val STATE_SINGLE = 3
        const val STATE_ERROR = 9

        private val HEX = "0123456789abcdef".toCharArray()
    }
}
