// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.storage

import com.coolappstore.evercallrecorder.by.svhp.core.L
import java.io.File

object BulkOps {
    fun deleteFiles(records: List<CallRecord>) {
        for (r in records) {
            // wrap each delete — file may already be gone or path may be invalid
            runCatching { File(r.uplinkPath).delete() }
                .onFailure { L.w("BulkOps", "delete uplink failed: ${it.message}") }
            r.downlinkPath?.let { p ->
                runCatching { File(p).delete() }
                    .onFailure { L.w("BulkOps", "delete downlink failed: ${it.message}") }
            }
        }
    }
}
