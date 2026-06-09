package com.coolappstore.evercallrecorder.by.svhp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.coolappstore.evercallrecorder.by.svhp.data.AppPreferences
import com.coolappstore.evercallrecorder.by.svhp.onboarding.OnboardingStatus
import com.coolappstore.evercallrecorder.by.svhp.ui.screens.DisclaimerScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.screens.HomeScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.screens.PermissionsScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.screens.SettingsScreen
import com.coolappstore.evercallrecorder.by.svhp.ui.theme.ShizucallrecorderTheme
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.AppNavigationViewModel
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.SettingsViewModel

@Composable
fun AppNavigationScreen() {
    val activityContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appNavViewModel: AppNavigationViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val onboardingStatus by appNavViewModel.onboardingStatus.collectAsState()
    val settingsViewModelUpdateTrigger by settingsViewModel.updateTrigger.collectAsState()
    val preferences = settingsViewModel.preferences

    // Tracks whether the Settings sub-screen is open on top of Home
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settingsViewModelUpdateTrigger) {
        val newStatus = OnboardingStatus.getStatus(activityContext, preferences)
        if (newStatus != onboardingStatus) {
            appNavViewModel.refresh()
        }
    }

    val screenState = resolveScreen(onboardingStatus)

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
    val dynamicColor = preferences.isDynamicColorEnabled()

    ShizucallrecorderTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        when (screenState) {
            AppScreen.Disclaimer -> DisclaimerScreen(
                onContinue = {
                    preferences.setDisclaimerAccepted(true)
                    appNavViewModel.refresh()
                }
            )
            AppScreen.Permissions -> PermissionsScreen(
                status              = onboardingStatus,
                onPermissionGranted = { appNavViewModel.refresh() }
            )
            AppScreen.Home -> {
                if (showSettings) {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onBack = { showSettings = false }
                    )
                } else {
                    HomeScreen(
                        appVersion = settingsViewModel.getAppVersion(),
                        onSettingsClick = { showSettings = true }
                    )
                }
            }
        }
    }
}

private enum class AppScreen { Disclaimer, Permissions, Home }

private fun resolveScreen(status: OnboardingStatus.Status): AppScreen {
    return when {
        !status.disclaimerAccepted -> AppScreen.Disclaimer
        !status.isComplete()       -> AppScreen.Permissions
        else                       -> AppScreen.Home
    }
}
