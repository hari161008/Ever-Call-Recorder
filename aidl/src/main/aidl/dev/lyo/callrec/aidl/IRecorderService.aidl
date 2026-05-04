// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.aidl;

import android.os.ParcelFileDescriptor;

/**
 * Bridge between the user-facing app process (UID u0_aXXX) and the privileged
 * recorder running inside the Shizuku UserService process (UID 2000 = shell).
 *
 * The AIDL transaction codes are pinned via the `= N` markers so that a daemon
 * UserService surviving an APK upgrade keeps a stable wire format with old
 * clients. Add new methods with the next free integer; never reuse a slot.
 */
interface IRecorderService {

    /**
     * Returns this service's BuildConfig.VERSION_CODE so the client can detect
     * a stale daemon (e.g. after an APK upgrade) and rebind with `remove=true`.
     */
    int getVersion() = 1;

    /**
     * Open two parallel AudioRecord instances and stream raw 16-bit little-endian
     * PCM into the supplied pipes. Caller writes to the pipe's read-end.
     *
     * @param uplinkSource   MediaRecorder.AudioSource (e.g. VOICE_UPLINK = 2, MIC = 1)
     * @param downlinkSource MediaRecorder.AudioSource (e.g. VOICE_DOWNLINK = 3)
     * @param sampleRate     16_000 recommended; 8_000 / 48_000 also valid
     * @param uplinkFd       write-end of pipe; service writes mono PCM
     * @param downlinkFd     write-end of pipe; service writes mono PCM
     *
     * @return result bitmask:
     *           bit0 (0x01) — uplink stream started successfully
     *           bit1 (0x02) — downlink stream started successfully
     *           0           — both failed; caller must release pfds and try fallback
     */
    int startDualRecord(
        int uplinkSource,
        int downlinkSource,
        int sampleRate,
        in ParcelFileDescriptor uplinkFd,
        in ParcelFileDescriptor downlinkFd
    ) = 2;

    /**
     * Single-stream fallback (used when dual-stream is denied by the audio HAL,
     * notably on Samsung One UI 5.1+).
     *
     * @param source       MediaRecorder.AudioSource (typically VOICE_CALL = 4 or MIC = 1)
     * @param sampleRate   16_000 / 8_000 / 48_000
     * @param channelMask  AudioFormat.CHANNEL_IN_MONO (16) or CHANNEL_IN_STEREO (12)
     * @param pcmFd        write-end of pipe; service writes interleaved PCM if stereo
     *
     * @return 1 on success, 0 on failure
     */
    int startSingleRecord(
        int source,
        int sampleRate,
        int channelMask,
        in ParcelFileDescriptor pcmFd
    ) = 3;

    /**
     * Reports the WrappedShellContext patch coverage so the app can detect
     * silent bypass degradation (a future AOSP minor renaming any of
     * sCurrentActivityThread / mSystemThread / mInitialApplication /
     * mBoundApplication produces a Degraded/Failed health here without
     * the recorder caching strategy-level failures).
     *
     * @return 0 = Failed (no patches applied — bypass non-functional),
     *         1 = Degraded (1-3 of 4 patches applied),
     *         2 = Full (all 4 applied; or root UID where bypass is unneeded)
     */
    int getBypassHealth() = 4;

    /** Stops any active recording. Safe to call from idle. */
    void stop() = 10;

    /**
     * @return one of:
     *           0 — idle
     *           1 — starting (transient)
     *           2 — recording dual
     *           3 — recording single
     *           9 — error (call getLastError for details)
     */
    int getState() = 11;

    /** Last error string from the recorder threads, or null. */
    @nullable
    String getLastError() = 12;

    // = 20 reserved (was probeSource — removed in v0.3, never used by app)

    // = 30 reserved (was grantPermission — removed in v0.3, never used by app)
}
