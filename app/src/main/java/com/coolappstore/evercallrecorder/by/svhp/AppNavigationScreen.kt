package com.coolappstore.evercallrecorder.by.svhp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
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

private enum class SubScreen(val stackDepth: Int) {
    None(0), Settings(1), Playback(1)
}

private val SlowEasing: Easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
private const val TRANSITION_MS = 480

private fun pushEnter() = (slideIn(
    animationSpec = tween(TRANSITION_MS, easing = SlowEasing)
) { IntOffset(it.width / 2, 0) } + fadeIn(tween(TRANSITION_MS, easing = SlowEasing)))

private fun pushExit() = (slideOut(
    animationSpec = tween(TRANSITION_MS, easing = SlowEasing)
) { IntOffset(-it.width / 4, 0) } + fadeOut(tween(TRANSITION_MS / 2, easing = SlowEasing)))

private fun popEnter() = (slideIn(
    animationSpec = tween(TRANSITION_MS, easing = SlowEasing)
) { IntOffset(-it.width / 4, 0) } + fadeIn(tween(TRANSITION_MS, easing = SlowEasing)))

private fun popExit() = (slideOut(
    animationSpec = tween(TRANSITION_MS, easing = SlowEasing)
) { IntOffset(it.width / 2, 0) } + fadeOut(tween(TRANSITION_MS / 2, easing = SlowEasing)))

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
    var prevDepth by remember { mutableIntStateOf(0) }

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
        val screen = resolveScreen(onboardingStatus)

        // Intercept hardware/gesture back on sub-screens
        BackHandler(enabled = subScreen != SubScreen.None) { goBack() }

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
                val isPush = subScreen.stackDepth >= prevDepth
                LaunchedEffect(subScreen) { prevDepth = subScreen.stackDepth }

                AnimatedContent(
                    targetState = subScreen,
                    transitionSpec = {
                        val entering = targetState.stackDepth >= initialState.stackDepth
                        if (entering) pushEnter() togetherWith pushExit()
                        else popEnter() togetherWith popExit()
                    },
                    label = "SubScreenTransition"
                ) { target ->
                    when (target) {
                        SubScreen.Settings -> SettingsScreen(
                            viewModel = settingsViewModel,
                            onBack = goBack
                        )
                        SubScreen.Playback -> {
                            val rec = selectedRecording
                            if (rec != null) {
                                PlaybackScreen(recording = rec, onBack = goBack)
                            }
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
            }
        }
    }
}

private fun resolveScreen(status: OnboardingStatus.Status): AppScreen = when {
    !status.disclaimerAccepted -> AppScreen.Disclaimer
    !status.isComplete()       -> AppScreen.Permissions
    else                       -> AppScreen.Home
}
