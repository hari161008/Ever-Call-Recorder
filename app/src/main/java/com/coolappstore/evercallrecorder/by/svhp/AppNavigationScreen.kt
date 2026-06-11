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
import android.os.Build

private enum class AppScreen { Disclaimer, Permissions, Home }
private enum class SubScreen(val depth: Int) { None(0), Settings(1), Playback(1) }

private val RevealEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
private const val DURATION_IN = 420
private const val DURATION_OUT = 280

private fun enterTransition() = fadeIn(tween(DURATION_IN, easing = RevealEasing)) + scaleIn(tween(DURATION_IN, easing = RevealEasing), initialScale = 0.94f)
private fun exitTransition() = fadeOut(tween(DURATION_OUT, easing = RevealEasing)) + scaleOut(tween(DURATION_OUT, easing = RevealEasing), targetScale = 0.97f)
private fun popEnterTransition() = fadeIn(tween(DURATION_IN, easing = RevealEasing)) + scaleIn(tween(DURATION_IN, easing = RevealEasing), initialScale = 0.97f)
private fun popExitTransition() = fadeOut(tween(DURATION_OUT, easing = RevealEasing)) + scaleOut(tween(DURATION_OUT, easing = RevealEasing), targetScale = 0.94f)

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
    var highlightQuery by remember { mutableStateOf("") }

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
        AppPreferences.ThemeMode.LIGHT, AppPreferences.ThemeMode.WHITE -> false
        AppPreferences.ThemeMode.DARK,  AppPreferences.ThemeMode.BLACK -> true
        AppPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppPreferences.ThemeMode.AUTO_WB -> isSystemInDarkTheme()
    }

    // Respect the user's dynamic color preference; fall back to accent color when off
    val isDynamicColor = remember(settingsUpdateTrigger) { preferences.isDynamicColorEnabled() }
    val accentArgb     = remember(settingsUpdateTrigger) { preferences.getAccentColor() }

    val systemIsDark = isSystemInDarkTheme()
    // Pure white/black override: background is pure white/black, but accent/dynamic still applies
    val isPureWhite = preferences.getThemeMode() == AppPreferences.ThemeMode.WHITE ||
                      (preferences.getThemeMode() == AppPreferences.ThemeMode.AUTO_WB && !systemIsDark)
    val isPureBlack = preferences.getThemeMode() == AppPreferences.ThemeMode.BLACK ||
                      (preferences.getThemeMode() == AppPreferences.ThemeMode.AUTO_WB && systemIsDark)
    val resolvedAccentArgb: Int? = if (!isDynamicColor) accentArgb else null
    val resolvedDynamicColor = isDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    ShizucallrecorderTheme(
        darkTheme    = darkTheme,
        dynamicColor = resolvedDynamicColor,
        accentArgb   = resolvedAccentArgb,
        isPureWhite  = isPureWhite,
        isPureBlack  = isPureBlack
    ) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                    AnimatedContent(
                        targetState = subScreen,
                        transitionSpec = {
                            val pushing = targetState.depth >= initialState.depth
                            if (pushing) enterTransition() togetherWith exitTransition()
                            else popEnterTransition() togetherWith popExitTransition()
                        },
                        label = "SubScreen"
                    ) { target ->
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            when (target) {
                                SubScreen.Settings -> SettingsScreen(viewModel = settingsViewModel, onBack = goBack)
                                SubScreen.Playback -> {
                                    val rec = selectedRecording
                                    if (rec != null) PlaybackScreen(recording = rec, onBack = goBack, highlightQuery = highlightQuery)
                                }
                                SubScreen.None -> HomeScreen(
                                    appVersion = settingsViewModel.getAppVersion(),
                                    onSettingsClick = { subScreen = SubScreen.Settings },
                                    onRecordingClick = { recording, query ->
                                        selectedRecording = recording
                                        highlightQuery = query
                                        subScreen = SubScreen.Playback
                                    }
                                )
                            }
                        }
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
