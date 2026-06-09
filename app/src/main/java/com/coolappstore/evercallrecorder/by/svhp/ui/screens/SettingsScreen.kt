/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.coolappstore.evercallrecorder.by.svhp.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolappstore.evercallrecorder.by.svhp.R
import com.coolappstore.evercallrecorder.by.svhp.data.AppPreferences
import com.coolappstore.evercallrecorder.by.svhp.integrations.scrcpy.ScrcpyAudioCodec
import com.coolappstore.evercallrecorder.by.svhp.integrations.scrcpy.ScrcpyAudioSource
import com.coolappstore.evercallrecorder.by.svhp.integrations.scrcpy.ScrcpyConfig
import com.coolappstore.evercallrecorder.by.svhp.system.PersistentFolderPickerContract
import com.coolappstore.evercallrecorder.by.svhp.system.copyToClipboard
import com.coolappstore.evercallrecorder.by.svhp.system.openGithub
import com.coolappstore.evercallrecorder.by.svhp.system.openGithubReportIssue
import com.coolappstore.evercallrecorder.by.svhp.system.storage.SafHelper
import com.coolappstore.evercallrecorder.by.svhp.system.takePersistableFolderPermission
import com.coolappstore.evercallrecorder.by.svhp.ui.common.ContactSelectionDialog
import com.coolappstore.evercallrecorder.by.svhp.ui.common.FileNameFormatDialog
import com.coolappstore.evercallrecorder.by.svhp.ui.common.M3DropdownField
import com.coolappstore.evercallrecorder.by.svhp.ui.common.OptionItem
import com.coolappstore.evercallrecorder.by.svhp.ui.common.ToggleListItem
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.ContactPickerState
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.ContactPickerType
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.ContactPickerViewModel
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.DebugAction
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.SettingsActions
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.SettingsViewModel
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val updateTrigger by viewModel.updateTrigger.collectAsState()
    val contactPickerViewModel: ContactPickerViewModel = viewModel()
    val contactPickerState by contactPickerViewModel.contactPickerState.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(PersistentFolderPickerContract()) { uri ->
        if (uri != null) {
            context.takePersistableFolderPermission(uri)
            viewModel.preferences.setRecordingFolderUri(uri)
        }
        viewModel.refresh()
    }

    val exportLogLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) viewModel.exportLogs(uri)
    }

    SettingsContent(
        preferences = viewModel.preferences,
        updateTrigger = updateTrigger,
        actions = viewModel,
        contactPickerState = contactPickerState,
        onSelectFolder = { folderPickerLauncher.launch(null) },
        onOpenContactsIncoming = { contactPickerViewModel.openContactPicker(ContactPickerType.INCOMING) },
        onOpenContactsOutgoing = { contactPickerViewModel.openContactPicker(ContactPickerType.OUTGOING) },
        onConfirmContacts = { numbers ->
            contactPickerViewModel.confirmContactPicker(numbers)
            viewModel.refresh()
        },
        onDismissContacts = { contactPickerViewModel.dismissContactPicker() },
        onExportLogs = { exportLogLauncher.launch("evercallrecorder_bug_report.log") },
        modifier = modifier
    )
}

@Composable
fun SettingsContent(
    preferences: AppPreferences,
    updateTrigger: Int,
    actions: SettingsActions,
    contactPickerState: ContactPickerState?,
    onSelectFolder: () -> Unit,
    onOpenContactsIncoming: () -> Unit,
    onOpenContactsOutgoing: () -> Unit,
    onConfirmContacts: (Set<String>) -> Unit,
    onDismissContacts: () -> Unit,
    onExportLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLicensesDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Hero header ──────────────────────────────────────────────────────
            item {
                SettingsHeader(appVersion = actions.getAppVersion())
            }

            // ── Sections ─────────────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(Modifier.height(8.dp))
                    AboutSection(
                        versionString = actions.getAppVersion(),
                        onShowLicenses = { showLicensesDialog = true }
                    )
                    RecordingSection(
                        preferences = preferences,
                        updateTrigger = updateTrigger,
                        actions = actions,
                        onSelectFolder = onSelectFolder,
                        onOpenContactsIncoming = onOpenContactsIncoming,
                        onOpenContactsOutgoing = onOpenContactsOutgoing
                    )
                    AudioSection(preferences, updateTrigger, actions)
                    SecuritySection(preferences, updateTrigger, actions)
                    VisualSection(preferences, updateTrigger, actions)
                    DebugSection(preferences, updateTrigger, actions, onExportLogs)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showLicensesDialog) {
        Dialog(
            onDismissRequest = { showLicensesDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.general_licenses),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    val libraries by produceLibraries(R.raw.aboutlibraries)
                    LibrariesContainer(
                        libraries,
                        Modifier.fillMaxSize().weight(1f),
                        showAuthor = true,
                        showLicenseBadges = true,
                        showFundingBadges = false,
                        showVersion = true,
                        showDescription = true
                    )
                    TextButton(
                        onClick = { showLicensesDialog = false },
                        modifier = Modifier.align(Alignment.End).padding(8.dp)
                    ) {
                        Text(stringResource(R.string.general_close))
                    }
                }
            }
        }
    }

    contactPickerState?.let { picker ->
        ContactSelectionDialog(
            title = when (picker.type) {
                ContactPickerType.INCOMING -> stringResource(R.string.settings_select_contacts_incoming)
                ContactPickerType.OUTGOING -> stringResource(R.string.settings_select_contacts_outgoing)
            },
            contacts = picker.contacts,
            initialSelection = picker.selectedNumbers,
            onConfirm = onConfirmContacts,
            onDismiss = onDismissContacts
        )
    }
}

// ── Hero Header ───────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsHeader(appVersion: String) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.linearGradient(listOf(primary, tertiary))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Ever Call Recorder",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = appVersion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Settings sections ──────────────────────────────────────────────────────────────────────

@Composable
private fun AboutSection(versionString: String, onShowLicenses: () -> Unit) {
    val context = LocalContext.current
    val serverVersion = ScrcpyConfig.SCRCPY_VERSION

    SettingsSection(
        title = stringResource(R.string.settings_section_about),
        icon = Icons.Outlined.Info
    ) {
        SectionListItem(
            icon = Icons.Outlined.Storage,
            headline = versionString,
            supporting = stringResource(R.string.settings_scrcpy_server, serverVersion)
        )
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { context.copyToClipboard("Scrcpy-Server Version", ScrcpyConfig.SCRCPY_VERSION) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) { Text(stringResource(R.string.settings_copy_version)) }
            OutlinedButton(
                onClick = onShowLicenses,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium
            ) { Text(stringResource(R.string.settings_view_licenses)) }
        }
        Button(
            onClick = { context.openGithub() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_open_github))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun VisualSection(preferences: AppPreferences, updateTrigger: Int, actions: SettingsActions) {
    val currentThemeMode = remember(updateTrigger) { preferences.getThemeMode() }
    val isDynamicColorEnabled = remember(updateTrigger) { preferences.isDynamicColorEnabled() }
    val isShowToastsEnabled = remember(updateTrigger) { preferences.isShowToastsEnabled() }
    val isVibrationEnabled = remember(updateTrigger) { preferences.isVibrationEnabled() }
    val context = LocalContext.current
    val resources = LocalResources.current

    val currentLanguage = remember {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) "" else currentLocales[0]?.toLanguageTag() ?: ""
    }

    val languageOptions = remember(context) {
        val options = mutableListOf(OptionItem("", resources.getString(R.string.settings_language_system)))
        @SuppressLint("DiscouragedApi")
        val resId = resources.getIdentifier("_generated_res_locale_config", "xml", context.packageName)
        try {
            val parser = resources.getXml(resId)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    val localeName = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
                    if (localeName != null) {
                        val locale = Locale.forLanguageTag(localeName)
                        val displayName = locale.getDisplayName(locale).replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                        }
                        options.add(OptionItem(localeName, displayName))
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            options.add(OptionItem("en", "English (Provided as fallback)"))
        }
        options.distinctBy { it.key }
    }

    SettingsSection(
        title = stringResource(R.string.settings_section_visual),
        icon = Icons.Outlined.Palette
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            M3DropdownField(
                label = stringResource(R.string.settings_language),
                selected = languageOptions.find { it.key == currentLanguage } ?: languageOptions.first(),
                options = languageOptions,
                onOptionSelected = { actions.setAppLanguage(it.key) }
            )
            val themeOptions = AppPreferences.ThemeMode.entries.map { mode ->
                val labelRes = when (mode) {
                    AppPreferences.ThemeMode.SYSTEM -> R.string.settings_theme_mode_system
                    AppPreferences.ThemeMode.LIGHT -> R.string.settings_theme_mode_light
                    AppPreferences.ThemeMode.DARK -> R.string.settings_theme_mode_dark
                }
                OptionItem(mode.key, stringResource(labelRes))
            }
            val defaultThemeMode = AppPreferences.DefaultsValue.THEME_MODE.key
            M3DropdownField(
                label = stringResource(R.string.settings_theme_mode),
                selected = themeOptions.find { it.key == currentThemeMode.key }
                    ?: themeOptions.find { it.key == defaultThemeMode }
                    ?: themeOptions.first(),
                options = themeOptions,
                onOptionSelected = { actions.setThemeMode(AppPreferences.ThemeMode.fromKey(it.key)) }
            )
        }
        ToggleListItem(label = stringResource(R.string.settings_dynamic_color), checked = isDynamicColorEnabled, onCheckedChange = { actions.setDynamicColorEnabled(it) })
        ToggleListItem(label = stringResource(R.string.settings_show_toasts), checked = isShowToastsEnabled, onCheckedChange = { actions.setShowToastsEnabled(it) })
        ToggleListItem(label = stringResource(R.string.settings_vibration_enabled), checked = isVibrationEnabled, onCheckedChange = { actions.setVibrationEnabled(it) })
    }
}

@Composable
private fun SecuritySection(preferences: AppPreferences, updateTrigger: Int, actions: SettingsActions) {
    val autoManageShizuku = remember(updateTrigger) { preferences.isShizukuAutoManageEnabled() }
    val shizukuStartOnRecord = remember(updateTrigger) { preferences.isShizukuStartOnRecordEnabled() }
    val shizukuKeepAlive = remember(updateTrigger) { preferences.isShizukuKeepAliveEnabled() }
    val shizukuAuthKey = remember(updateTrigger) { preferences.getShizukuAuthKey() }

    SettingsSection(
        title = stringResource(R.string.settings_section_security),
        icon = Icons.Outlined.Shield
    ) {
        ToggleListItem(
            label = stringResource(R.string.settings_shizuku_auto_manage),
            checked = autoManageShizuku,
            onCheckedChange = { actions.setShizukuAutoManageEnabled(it) },
            description = stringResource(R.string.settings_shizuku_auto_manage_desc)
        )
        AnimatedVisibility(visible = autoManageShizuku, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                var textState by remember(shizukuAuthKey) { mutableStateOf(shizukuAuthKey) }
                val keyboardController = LocalSoftwareKeyboardController.current
                var isFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text(stringResource(R.string.settings_shizuku_auth_key)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .onFocusChanged { isFocused = it.isFocused },
                    singleLine = true,
                    visualTransformation = if (isFocused) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password, showKeyboardOnFocus = true),
                    keyboardActions = KeyboardActions(onDone = {
                        actions.setShizukuAuthKey(textState)
                        keyboardController?.hide()
                    })
                )
                ToggleListItem(label = stringResource(R.string.settings_shizuku_start_on_record), checked = shizukuStartOnRecord, onCheckedChange = { actions.setShizukuStartOnRecordEnabled(it) }, description = stringResource(R.string.settings_shizuku_start_on_record_desc))
                ToggleListItem(label = stringResource(R.string.settings_shizuku_keep_alive), checked = shizukuKeepAlive, onCheckedChange = { actions.setShizukuKeepAliveEnabled(it) }, description = stringResource(R.string.settings_shizuku_keep_alive_desc))
            }
        }
    }
}

@Composable
private fun RecordingSection(
    preferences: AppPreferences,
    updateTrigger: Int,
    actions: SettingsActions,
    onSelectFolder: () -> Unit,
    onOpenContactsIncoming: () -> Unit,
    onOpenContactsOutgoing: () -> Unit
) {
    val context = LocalContext.current
    val recordingFolderLabel = remember(updateTrigger) { SafHelper.getFolderDisplayNameOrNull(context, preferences.getRecordingFolderUri()) }
    val fileNameFormat = remember(updateTrigger) { preferences.getFileNameTemplate() }
    val autoRecordIncoming = remember(updateTrigger) { preferences.isAutoRecordIncomingEnabled() }
    val autoRecordOutgoing = remember(updateTrigger) { preferences.isAutoRecordOutgoingEnabled() }
    val ignoreAnonymousIncoming = remember(updateTrigger) { preferences.isIgnoreAnonymousIncomingEnabled() }
    val ignoreCrossCountryIncoming = remember(updateTrigger) { preferences.isIgnoreCrossCountryIncomingEnabled() }
    val ignoreContactsModeIncoming = remember(updateTrigger) { preferences.getIgnoreContactsModeIncoming() }
    val ignoreContactsModeOutgoing = remember(updateTrigger) { preferences.getIgnoreContactsModeOutgoing() }
    val ignoreCrossCountryOutgoing = remember(updateTrigger) { preferences.isIgnoreCrossCountryOutgoingEnabled() }
    val ignoredContactsIncomingCount = remember(updateTrigger) { preferences.getIgnoredContactsIncoming().size }
    val ignoredContactsOutgoingCount = remember(updateTrigger) { preferences.getIgnoredContactsOutgoing().size }
    var showFileNameFormatDialog by remember { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(R.string.settings_section_recording),
        icon = Icons.Outlined.FiberManualRecord
    ) {
        // Folder picker row
        SectionListItem(
            icon = Icons.Outlined.Folder,
            headline = stringResource(R.string.settings_recording_folder_label),
            supporting = recordingFolderLabel ?: stringResource(R.string.settings_tap_to_select_folder),
            supportingColor = MaterialTheme.colorScheme.primary,
            onClick = onSelectFolder
        )
        // File name row
        SectionListItem(
            icon = Icons.Outlined.DriveFileRenameOutline,
            headline = stringResource(R.string.settings_file_name_template),
            supporting = fileNameFormat,
            supportingColor = MaterialTheme.colorScheme.primary,
            onClick = { showFileNameFormatDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), thickness = 0.5.dp)

        ToggleListItem(label = stringResource(R.string.settings_auto_record_incoming), checked = autoRecordIncoming, onCheckedChange = { actions.setAutoRecordIncoming(it) })
        AnimatedVisibility(visible = autoRecordIncoming, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                ToggleListItem(label = stringResource(R.string.settings_ignore_anonymous_incoming), checked = ignoreAnonymousIncoming, onCheckedChange = { actions.setIgnoreAnonymousIncoming(it) })
                ToggleListItem(label = stringResource(R.string.settings_ignore_cross_country_incoming), checked = ignoreCrossCountryIncoming, onCheckedChange = { actions.setIgnoreCrossCountryIncoming(it) }, enabled = ignoreAnonymousIncoming)
                IgnoreContactsOptions(label = stringResource(R.string.settings_ignore_contacts_incoming), selectedEnum = ignoreContactsModeIncoming, selectedCount = ignoredContactsIncomingCount, onSelected = { actions.setIgnoreContactsModeIncoming(it) }, onSelectContacts = onOpenContactsIncoming)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), thickness = 0.5.dp)

        ToggleListItem(label = stringResource(R.string.settings_auto_record_outgoing), checked = autoRecordOutgoing, onCheckedChange = { actions.setAutoRecordOutgoing(it) })
        AnimatedVisibility(visible = autoRecordOutgoing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                ToggleListItem(label = stringResource(R.string.settings_ignore_cross_country_outgoing), checked = ignoreCrossCountryOutgoing, onCheckedChange = { actions.setIgnoreCrossCountryOutgoing(it) })
                IgnoreContactsOptions(label = stringResource(R.string.settings_ignore_contacts_outgoing), selectedEnum = ignoreContactsModeOutgoing, selectedCount = ignoredContactsOutgoingCount, onSelected = { actions.setIgnoreContactsModeOutgoing(it) }, onSelectContacts = onOpenContactsOutgoing)
            }
        }
    }

    if (showFileNameFormatDialog) {
        FileNameFormatDialog(
            initialFormat = fileNameFormat,
            onConfirm = { format ->
                actions.setFileNameTemplate(format)
                showFileNameFormatDialog = false
            },
            onDismiss = { showFileNameFormatDialog = false }
        )
    }
}

@Composable
private fun AudioSection(preferences: AppPreferences, updateTrigger: Int, actions: SettingsActions) {
    val isDebugEnabled = remember(updateTrigger) { preferences.isDebugEnabled() }
    val audioSource = remember(updateTrigger) { preferences.getAudioSource() }
    val audioCodec = remember(updateTrigger) { preferences.getAudioCodec() }
    val savedBitRate = remember(updateTrigger) { preferences.getAudioBitRate() }

    SettingsSection(
        title = stringResource(R.string.settings_section_audio),
        icon = Icons.Outlined.Equalizer
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val currentSdk = Build.VERSION.SDK_INT
            val audioSourceOptions = ScrcpyAudioSource.entries
                .filter { !it.isDebugOnly || isDebugEnabled }
                .map { source ->
                    OptionItem(
                        key = source.cliKey,
                        label = stringResource(source.titleResId),
                        description = stringResource(source.descriptionResId),
                        enabled = currentSdk >= source.minApi && (source.maxApi == null || currentSdk <= source.maxApi)
                    )
                }
            val selectedAudio = audioSourceOptions.find { it.key == audioSource } ?: audioSourceOptions.first()
            M3DropdownField(label = stringResource(R.string.settings_audio_source), selected = selectedAudio, options = audioSourceOptions, onOptionSelected = { actions.setAudioSource(it.key) })
            selectedAudio.description?.let { desc ->
                Text(text = desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            }
            val codecOptions = ScrcpyAudioCodec.entries.map { OptionItem(it.cliKey, stringResource(it.titleResId)) }
            M3DropdownField(label = stringResource(R.string.settings_audio_codec), selected = codecOptions.find { it.key == audioCodec } ?: codecOptions.first(), options = codecOptions, onOptionSelected = { actions.setAudioCodec(it.key) })
            if (!LocalInspectionMode.current && audioCodec != ScrcpyAudioCodec.AAC.cliKey) {
                Text(text = stringResource(R.string.settings_audio_bitrate_recommendation), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            }
            val bitrateOptions = listOf(8000, 16000, 32000, 64000, 128000).map { OptionItem(it.toString(), stringResource(R.string.audio_bitrate_kbps, it / 1000)) }
            M3DropdownField(label = stringResource(R.string.settings_audio_bitrate), selected = bitrateOptions.find { it.key == savedBitRate.toString() } ?: bitrateOptions.first(), options = bitrateOptions, onOptionSelected = { actions.setAudioBitRate(it.key.toInt()) })
        }
    }
}

@Composable
private fun DebugSection(preferences: AppPreferences, updateTrigger: Int, actions: SettingsActions, onExportLogs: () -> Unit) {
    val isDebugEnabled = remember(updateTrigger) { preferences.isDebugEnabled() }
    val debugCallerNumber = remember(updateTrigger) { preferences.getDebugCallerNumber() }
    val isLoggingEnabled = remember(updateTrigger) { preferences.isLoggingEnabled() }
    val context = LocalContext.current

    SettingsSection(
        title = stringResource(R.string.settings_section_debug),
        icon = Icons.Outlined.BugReport
    ) {
        ToggleListItem(label = stringResource(R.string.settings_debug_logging_enabled), checked = isLoggingEnabled, onCheckedChange = { actions.setLoggingEnabled(it) }, description = if (!isLoggingEnabled) stringResource(R.string.settings_debug_logging_enabled_description) else null)
        if (isLoggingEnabled) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = stringResource(R.string.settings_debug_logging_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                Text(text = stringResource(R.string.settings_debug_logging_steps), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(R.string.settings_debug_logging_step_warning), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                if (isDebugEnabled) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(text = stringResource(R.string.settings_debug_logging_step_warning_no_redaction), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onExportLogs, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_debug_logging_generate_report)) }
                    OutlinedButton(onClick = { context.openGithubReportIssue() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings_debug_logging_report_on_github)) }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), thickness = 0.5.dp)
        ToggleListItem(label = stringResource(R.string.settings_debug_mode), checked = isDebugEnabled, onCheckedChange = { actions.setDebugEnabled(it) }, description = stringResource(R.string.settings_debug_mode_description))
        if (isDebugEnabled) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var textState by remember(debugCallerNumber) { mutableStateOf(debugCallerNumber) }
                val allowedChars = "^[0-9+-]*$".toRegex()
                val keyboardController = LocalSoftwareKeyboardController.current
                OutlinedTextField(
                    value = textState,
                    onValueChange = { newValue ->
                        if (newValue.matches(allowedChars)) {
                            textState = newValue
                            actions.setDebugCallerNumber(newValue)
                        }
                    },
                    label = { Text(stringResource(R.string.settings_debug_caller_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Phone, showKeyboardOnFocus = true),
                    keyboardActions = KeyboardActions(onDone = {
                        actions.setDebugCallerNumber(textState)
                        keyboardController?.hide()
                    })
                )
                DebugActionGrid(actions)
            }
        }
    }
}

// ── Internal helper composables ────────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Section header row with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SectionListItem(
    icon: ImageVector,
    headline: String,
    supporting: String? = null,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null
) {
    val mod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ListItem(
        modifier = mod,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        headlineContent = { Text(headline, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = supporting?.let { { Text(it, color = supportingColor, style = MaterialTheme.typography.bodySmall) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun IgnoreContactsOptions(
    label: String,
    selectedEnum: AppPreferences.IgnoreContactsMode,
    selectedCount: Int,
    onSelected: (AppPreferences.IgnoreContactsMode) -> Unit,
    onSelectContacts: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.height(8.dp))
        AppPreferences.IgnoreContactsMode.entries.forEach { ignoreContactMode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(ignoreContactMode) }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(selected = selectedEnum == ignoreContactMode, onClick = { onSelected(ignoreContactMode) })
                Text(
                    text = when (ignoreContactMode) {
                        AppPreferences.IgnoreContactsMode.NONE -> stringResource(R.string.settings_ignore_contacts_none)
                        AppPreferences.IgnoreContactsMode.ALL -> stringResource(R.string.settings_ignore_contacts_all)
                        AppPreferences.IgnoreContactsMode.SELECTED -> stringResource(R.string.settings_ignore_contacts_selected)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (selectedEnum == AppPreferences.IgnoreContactsMode.SELECTED) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onSelectContacts, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.settings_select_contacts, selectedCount))
            }
        }
    }
}

@Composable
private fun DebugActionGrid(actions: SettingsActions) {
    val items = listOf(
        DebugAction.RINGING to stringResource(R.string.settings_debug_action_ringing),
        DebugAction.OFFHOOK to stringResource(R.string.settings_debug_action_offhook),
        DebugAction.IDLE to stringResource(R.string.settings_debug_action_idle)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (action, label) ->
            FilledTonalButton(onClick = { actions.triggerDebugAction(action) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        val mockContext = LocalContext.current
        val dummyPreferences = AppPreferences(mockContext)
        val dummyActions = object : SettingsActions {
            override fun setAutoRecordIncoming(enabled: Boolean) {}
            override fun setAutoRecordOutgoing(enabled: Boolean) {}
            override fun setVibrationEnabled(enabled: Boolean) {}
            override fun setIgnoreAnonymousIncoming(enabled: Boolean) {}
            override fun setIgnoreCrossCountryIncoming(enabled: Boolean) {}
            override fun setIgnoreCrossCountryOutgoing(enabled: Boolean) {}
            override fun setIgnoreContactsModeIncoming(modeEnum: AppPreferences.IgnoreContactsMode) {}
            override fun setIgnoreContactsModeOutgoing(modeEnum: AppPreferences.IgnoreContactsMode) {}
            override fun setAudioSource(source: String) {}
            override fun setAudioCodec(codec: String) {}
            override fun setAudioBitRate(bitRate: Int) {}
            override fun setThemeMode(mode: AppPreferences.ThemeMode) {}
            override fun setDynamicColorEnabled(enabled: Boolean) {}
            override fun setShowToastsEnabled(enabled: Boolean) {}
            override fun setAppLanguage(languageCode: String) {}
            override fun setLoggingEnabled(enabled: Boolean) {}
            override fun setDebugEnabled(enabled: Boolean) {}
            override fun setDebugCallerNumber(number: String) {}
            override fun triggerDebugAction(action: DebugAction) {}
            override fun exportLogs(uri: Uri) {}
            override fun getAppVersion(): String = "Version 2.0.0 (Mock)"
            override fun setShizukuAutoManageEnabled(enabled: Boolean) {}
            override fun setShizukuStartOnRecordEnabled(enabled: Boolean) {}
            override fun setShizukuKeepAliveEnabled(enabled: Boolean) {}
            override fun setShizukuAuthKey(key: String) {}
            override fun setFileNameTemplate(template: String) {}
        }
        SettingsContent(
            preferences = dummyPreferences,
            updateTrigger = 0,
            actions = dummyActions,
            contactPickerState = null,
            onSelectFolder = {},
            onOpenContactsIncoming = {},
            onOpenContactsOutgoing = {},
            onConfirmContacts = {},
            onDismissContacts = {},
            onExportLogs = {}
        )
    }
}
