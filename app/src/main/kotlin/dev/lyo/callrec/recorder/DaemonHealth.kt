// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.recorder

import com.coolappstore.evercallrecorder.by.svhp.aidl.IRecorderService

/**
 * Single source of truth for the privileged-recorder pipeline state, observed
 * from the app process. Combines Shizuku availability, permission, and daemon
 * binding into one composite signal (previously split across a coarse enum and
 * a separate `service: StateFlow<IRecorderService?>` flow).
 *
 * Drivers (in [ShizukuClient]):
 *   - `Shizuku.pingBinder()` distinguishes [NotInstalled]/[NotRunning]
 *   - `Shizuku.checkSelfPermission()` distinguishes [NoPermission]
 *   - `ServiceConnection.onServiceConnected` carries the live binder for [Bound]
 *   - daemon `getVersion()` mismatch yields [Stale]
 *   - daemon `getVersion()` throws yields [Unhealthy]
 */
sealed interface DaemonHealth {
    /** Shizuku app not installed on device. */
    data object NotInstalled : DaemonHealth
    /** Shizuku installed but not running. */
    data object NotRunning : DaemonHealth
    /** Shizuku running but our package has not been granted permission. */
    data object NoPermission : DaemonHealth
    /** Daemon binder alive but its `getVersion` differs from our build constant. */
    data object Stale : DaemonHealth
    /** Happy path: daemon bound, version matches, AIDL calls work. */
    data class Bound(val service: IRecorderService) : DaemonHealth
    /** Daemon process alive but AIDL calls fail (zombie / partial crash). */
    data class Unhealthy(val reason: String) : DaemonHealth
}
