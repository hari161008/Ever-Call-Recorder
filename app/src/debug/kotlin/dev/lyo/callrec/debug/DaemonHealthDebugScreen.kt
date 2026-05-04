// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.debug

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coolappstore.evercallrecorder.by.svhp.App
import com.coolappstore.evercallrecorder.by.svhp.recorder.DaemonHealth
import kotlinx.coroutines.launch

/**
 * Debug-only diagnostic surface. Lives in `app/src/debug/` — release builds
 * exclude it entirely. Shows current bypass health, daemon health, and
 * capability cache contents to aid device-matrix triage without shipping
 * a debug pane in release.
 */
@Composable
fun DaemonHealthDebugScreen() {
    val context = LocalContext.current
    val container = (context.applicationContext as App).container
    val health by container.shizuku.health.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var bypass by remember { mutableStateOf<Int?>(null) }
    var caps by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(health) {
        bypass = (health as? DaemonHealth.Bound)?.service?.let {
            runCatching { it.bypassHealth }.getOrNull()
        }
        caps = runCatching { container.capabilities.current()?.toString() }.getOrNull() ?: "—"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Build.FINGERPRINT", style = MaterialTheme.typography.labelMedium)
        Text(Build.FINGERPRINT)
        Spacer(Modifier.height(12.dp))

        Text("DaemonHealth", style = MaterialTheme.typography.labelMedium)
        Text(health.toString())
        Spacer(Modifier.height(12.dp))

        Text("BypassHealth", style = MaterialTheme.typography.labelMedium)
        Text(bypass?.let { listOf("Failed", "Degraded", "Full").getOrElse(it) { "?" } } ?: "—")
        Spacer(Modifier.height(12.dp))

        Text("Capabilities", style = MaterialTheme.typography.labelMedium)
        Text(caps ?: "—")
        Spacer(Modifier.height(24.dp))

        Button(onClick = {
            scope.launch { container.capabilities.clear() }
        }) { Text("Clear capability cache") }
    }
}
