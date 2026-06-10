package com.coolappstore.evercallrecorder.by.svhp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolappstore.evercallrecorder.by.svhp.data.AppPreferences
import com.coolappstore.evercallrecorder.by.svhp.onboarding.OnboardingStatus
import com.coolappstore.evercallrecorder.by.svhp.ui.screens.DisclaimerScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.screens.HomeScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.screens.PermissionsScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.screens.PlaybackScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.screens.SettingsScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.theme.ShizucallrecorderTheme
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.AppNavigationViewModel
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.RecordingItem
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.SettingsViewModel

private enum class AppScreen { Disclaimer, Permissions, Home }
private enum class SubScreen(val depth: Int) { None(0), Settings(1), Playback(1) }

private val RevealEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
private const val DURATION_IN = 420
private const val DURATION_OUT = 280

private fun enterTransition() =
    fadeIn(tween(DURATION_IN, easing = RevealEasing)) +
    scaleIn(tween(DURATION_IN, easing = RevealEasing), initialScale = 0.94f)

private fun exitTransition() =
    fadeOut(tween(DURATION_OUT, easing = RevealEasing)) +
    scaleOut(tween(DURATION_OUT, easing = RevealEasing), targetScale = 0.97f)

private fun popEnterTransition() =
    fadeIn(tween(DURATION_IN, easing = RevealEasing)) +
    scaleIn(tween(DURATION_IN, easing = RevealEasing), initialScale = 0.97f)

private fun popExitTransition() =
    fadeOut(tween(DURATION_OUT, easing = RevealEasing)) +
    scaleOut(tween(DURATION_OUT, easing = RevealEasing), targetScale = 0.94f)

@Composable
fun AppNavigationScreen() {
    val activityContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appNavViewModel: AppNavigationViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val onboardingStatus by appNavViewModel.onboardingStatus.collectAsState()
    val settingsUpdateTrigger by settingsViewModel.updateTrigger.collectAsState()
    val preferences = settingsViewModel.preferences

    var subScreen by rememberSaveable { mutableStateOf(SubScreen.None) }
    var selectedRecording by remember { mutableStateOf<RecordingItem?>(null) }

    val goBack: () -> Unit = { subScreen = SubScreen.None }

    LaunchedEffect(settingsUpdateTrigger) {
        val newStatus = OnboardingStatus.getStatus(activityContext, preferences)
        if (newStatus != onboardingStatus) appNavViewModel.refresh()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                appNavViewModel.refresh()
                settingsViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val darkTheme = when (preferences.getThemeMode()) {
        AppPreferences.ThemeMode.LIGHT -> false
        AppPreferences.ThemeMode.DARK  -> true
        AppPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    ShizucallrecorderTheme(darkTheme = darkTheme) {
        // Solid background behind ALL transitions — prevents any dark flicker
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            BackHandler(enabled = subScreen != SubScreen.None) { goBack() }

            val screen = resolveScreen(onboardingStatus)
            when (screen) {
                AppScreen.Disclaimer -> DisclaimerScreen(
                    onContinue = {
                        preferences.setDisclaimerAccepted(true)
                        appNavViewModel.refresh()
                    }
                )
                AppScreen.Permissions -> PermissionsScreen(
                    status = onboardingStatus,
                    onPermissionGranted = { appNavViewModel.refresh() }
                )
                AppScreen.Home -> {
                    var prevDepth by remember { mutableIntStateOf(0) }

                    AnimatedContent(
                        targetState = subScreen,
                        transitionSpec = {
                            val pushing = targetState.depth >= initialState.depth
                            if (pushing)
                                enterTransition() togetherWith exitTransition()
                            else
                                popEnterTransition() togetherWith popExitTransition()
                        },
                        label = "SubScreen"
                    ) { target ->
                        // Background behind each screen so nothing bleeds through
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            when (target) {
                                SubScreen.Settings -> SettingsScreen(
                                    viewModel = settingsViewModel,
                                    onBack = goBack
                                )
                                SubScreen.Playback -> {
                                    val rec = selectedRecording
                                    if (rec != null) PlaybackScreen(recording = rec, onBack = goBack)
                                }
                                SubScreen.None -> HomeScreen(
                                    appVersion = settingsViewModel.getAppVersion(),
                                    onSettingsClick = { subScreen = SubScreen.Settings },
                                    onRecordingClick = { recording ->
                                        selectedRecording = recording
                                        subScreen = SubScreen.Playback
                                    }
                                )
                            }
                        }
                        LaunchedEffect(target) { prevDepth = target.depth }
                    }
                }
            }
        }
    }
}

private fun resolveScreen(status: OnboardingStatus.Status): AppScreen = when {
    !status.disclaimerAccepted -> AppScreen.Disclaimer
    !status.isComplete()       -> AppScreen.Permissions
    else                       -> AppScreen.Home
}
