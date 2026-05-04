// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.coolappstore.evercallrecorder.by.svhp.di.AppContainer
import com.coolappstore.evercallrecorder.by.svhp.permissions.SetupStatus
import com.coolappstore.evercallrecorder.by.svhp.ui.legal.LegalDisclaimerSheet
import com.coolappstore.evercallrecorder.by.svhp.ui.onboarding.OnboardingScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.playback.PlaybackScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.primary.PrimaryScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * Routing destinations. The string IDs are stable across app upgrades — the
 * navigation graph state-restoration relies on them.
 */
object Routes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Settings = "settings"
    fun playback(callId: String) = "playback/$callId"
    const val PlaybackPattern = "playback/{callId}"
}

@Composable
fun CallrecApp(
    container: AppContainer,
    startWithOnboarding: Boolean,
    /** Optional callId from the completed-recording notification deep link. */
    initialPlaybackCallId: String? = null,
    nav: NavHostController = rememberNavController(),
) {
    // Deep link from CompletedRecordingNotification: jump straight into
    // the playback screen for the just-saved record. Done with a one-shot
    // LaunchedEffect keyed on the id so subsequent navigation isn't hijacked.
    LaunchedEffect(initialPlaybackCallId) {
        val id = initialPlaybackCallId ?: return@LaunchedEffect
        nav.navigate(Routes.playback(id)) { launchSingleTop = true }
    }
    val daemonHealth by container.shizuku.health.collectAsStateWithLifecycle()
    val start = if (startWithOnboarding) Routes.Onboarding else Routes.Home

    // First-run legal placeholder. We default to `true` to avoid a flash of
    // the sheet for returning users while DataStore is loading. The flag is
    // versioned (`disclaimer_accepted_v1`), so a future material text change
    // can be re-published by bumping to `_v2`.
    val disclaimerAccepted by container.settings.disclaimerAccepted
        .collectAsStateWithLifecycle(initialValue = true)
    val disclaimerScope = rememberCoroutineScope()

    // On every Activity RESUME re-check setup. If any prerequisite fell off
    // (Shizuku service died, overlay permission revoked, battery exemption
    // revoked, etc) — bounce them back to Onboarding so they understand WHY
    // recording stopped working.
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(lifecycle) {
        lifecycle.currentStateFlow.collect { stage ->
            if (stage.isAtLeast(Lifecycle.State.RESUMED)) {
                val status = SetupStatus.probe(ctx, container)
                if (!status.allReady &&
                    currentRoute != null &&
                    currentRoute != Routes.Onboarding
                ) {
                    nav.navigate(Routes.Onboarding) {
                        popUpTo(Routes.Home) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = nav,
        startDestination = start,
        // Material 3 Expressive favours subtle vertical translation over the
        // platform default horizontal slide — feels lighter and works well
        // edge-to-edge.
        enterTransition = {
            fadeIn(animationSpec = tween(220)) +
                slideInVertically(animationSpec = tween(280)) { it / 16 }
        },
        exitTransition = {
            fadeOut(animationSpec = tween(180)) +
                slideOutVertically(animationSpec = tween(220)) { -it / 32 }
        },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
        popExitTransition = { fadeOut(animationSpec = tween(160)) },
    ) {
        composable(Routes.Onboarding) {
            OnboardingScreen(
                container = container,
                onDone = {
                    nav.navigate(Routes.Home) {
                        popUpTo(Routes.Onboarding) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Home) {
            PrimaryScreen(
                container = container,
                daemonHealth = daemonHealth,
                onOpenSettings = { nav.navigate(Routes.Settings) },
                onOpenPlayback = { id -> nav.navigate(Routes.playback(id)) },
            )
        }
        composable(Routes.Settings) {
            SettingsScreen(
                container = container,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.PlaybackPattern) { entry ->
            val id = entry.arguments?.getString("callId").orEmpty()
            PlaybackScreen(
                container = container,
                callId = id,
                onBack = { nav.popBackStack() },
            )
        }
    }

    if (!disclaimerAccepted) {
        LegalDisclaimerSheet(
            requireAck = true,
            onAccept = {
                disclaimerScope.launch {
                    container.settings.setDisclaimerAccepted(true)
                }
            },
            onDismiss = { /* unused while requireAck = true */ },
        )
    }
    }
}
