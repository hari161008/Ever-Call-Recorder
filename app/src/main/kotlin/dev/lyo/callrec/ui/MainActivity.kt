// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.coolappstore.evercallrecorder.by.svhp.App
import com.coolappstore.evercallrecorder.by.svhp.notify.CompletedRecordingNotification
import com.coolappstore.evercallrecorder.by.svhp.notify.DaemonHealthNotification
import com.coolappstore.evercallrecorder.by.svhp.permissions.SetupStatus
import com.coolappstore.evercallrecorder.by.svhp.ui.nav.CallrecApp
import com.coolappstore.evercallrecorder.by.svhp.ui.theme.CallrecTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingCallId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("Callrec", "[MainActivity] onCreate begin")
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.value.toInt(), Color.Transparent.value.toInt()),
            navigationBarStyle = SystemBarStyle.auto(Color.Transparent.value.toInt(), Color.Transparent.value.toInt()),
        )

        val container = (application as App).container
        container.shizuku.attach()
        container.shizuku.refresh()
        keepSplash = false

        val initialStatus = SetupStatus.probe(this, container)
        Log.i("Callrec", "[MainActivity] initial setup status: $initialStatus")
        pendingCallId = intent?.getStringExtra(CompletedRecordingNotification.EXTRA_OPEN_CALL_ID)

        setContent {
            CallrecTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CallrecApp(
                        container = container,
                        startWithOnboarding = !initialStatus.allReady,
                        initialPlaybackCallId = pendingCallId,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)  // so Compose seeing intent.extras gets the latest

        // Activity is `singleTop` per the manifest — when the user taps a
        // notification while we're already running, we land here instead of
        // onCreate.

        // Completed recording tap — deep-link to playback
        intent.getStringExtra(CompletedRecordingNotification.EXTRA_OPEN_CALL_ID)?.let {
            pendingCallId = it
        }

        // Daemon health notification tap — trigger re-check
        if (intent.getBooleanExtra(DaemonHealthNotification.EXTRA_FROM_HEALTH_NOTIF, false)) {
            val container = (application as App).container
            container.appScope.launch {
                container.shizuku.verifyHealth()
                container.shizuku.refresh()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (application as App).container.shizuku.refresh()
    }

    override fun onDestroy() {
        (application as App).container.shizuku.detach()
        super.onDestroy()
    }
}
