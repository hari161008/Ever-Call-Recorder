/*
 * ShizuCallRecorder: FOSS Call recording powered through ADB/Shizuku!
 *  Copyright (C) 2026-present kitsumed (Med)
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.coolappstore.evercallrecorder.by.svhp.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import com.coolappstore.evercallrecorder.by.svhp.integrations.scrcpy.ScrcpyAudioCodec
import com.coolappstore.evercallrecorder.by.svhp.integrations.scrcpy.ScrcpyAudioSource

class AppPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "evercallrecorder_prefs"
        // Default accent color: original green (ARGB packed as Int)
        // Equivalent to Color(0xFF386B20): alpha=255, R=56, G=107, B=32
        val DEFAULT_ACCENT_ARGB: Int = (255 shl 24) or (0x38 shl 16) or (0x6B shl 8) or 0x20
    }

    object DefaultsValue {
        const val DISCLAIMER_ACCEPTED = false
        val RECORDING_FOLDER_URI: String? = null
        const val VIBRATION_ENABLED = true
        const val AUTO_RECORD_INCOMING = false
        const val AUTO_RECORD_OUTGOING = false
        const val RECORD_ON_ANSWER = true
        const val IGNORE_ANONYMOUS_INCOMING = false
        const val IGNORE_CROSS_COUNTRY_INCOMING = false
        const val IGNORE_CROSS_COUNTRY_OUTGOING = false
        val IGNORE_CONTACTS_MODE_INCOMING = IgnoreContactsMode.NONE
        val IGNORE_CONTACTS_MODE_OUTGOING = IgnoreContactsMode.NONE
        val IGNORED_CONTACTS_INCOMING = emptySet<String>()
        val IGNORED_CONTACTS_OUTGOING = emptySet<String>()
        const val LOGGING_ENABLED = false
        const val DEBUG_ENABLED = false
        const val DEBUG_CALLER_NUMBER = ""
        val AUDIO_SOURCE = ScrcpyAudioSource.VOICE_CALL.cliKey
        val AUDIO_CODEC = ScrcpyAudioCodec.OPUS.cliKey
        val AUDIO_BITRATE = ScrcpyAudioCodec.OPUS.defaultBitRate
        const val FILE_NAME_TEMPLATE = "{date}_{direction}_{phone_number}"
        val THEME_MODE = ThemeMode.SYSTEM
        const val DYNAMIC_COLOR = true
        const val SHOW_TOASTS = true
        const val SHIZUKU_AUTO_MANAGE = false
        const val SHIZUKU_START_ON_RECORD = false
        const val SHIZUKU_KEEP_ALIVE = false
        const val SHIZUKU_AUTH_KEY = ""
        // Accent color default: original green
        val ACCENT_COLOR: Int = DEFAULT_ACCENT_ARGB
    }

    enum class Key(val id: String) {
        DISCLAIMER_ACCEPTED("disclaimer_accepted"),
        RECORDING_FOLDER_URI("recording_folder_uri"),
        VIBRATION_ENABLED("vibration_enabled"),
        AUTO_RECORD_INCOMING("auto_record_incoming"),
        AUTO_RECORD_OUTGOING("auto_record_outgoing"),
        RECORD_ON_ANSWER("record_on_answer"),
        IGNORE_ANONYMOUS_INCOMING("ignore_anonymous_incoming"),
        IGNORE_CROSS_COUNTRY_INCOMING("ignore_cross_country_incoming"),
        IGNORE_CROSS_COUNTRY_OUTGOING("ignore_cross_country_outgoing"),
        IGNORE_CONTACTS_MODE_INCOMING("ignore_contacts_mode_incoming"),
        IGNORE_CONTACTS_MODE_OUTGOING("ignore_contacts_mode_outgoing"),
        IGNORED_CONTACTS_INCOMING("ignored_contacts_incoming"),
        IGNORED_CONTACTS_OUTGOING("ignored_contacts_outgoing"),
        LOGGING_ENABLED("logging_enabled"),
        DEBUG_ENABLED("debug_enabled"),
        DEBUG_CALLER_NUMBER("debug_caller_number"),
        AUDIO_SOURCE("audio_source"),
        AUDIO_CODEC("audio_codec"),
        AUDIO_BITRATE("audio_bitrate"),
        FILE_NAME_TEMPLATE("file_name_template"),
        THEME_MODE("theme_mode"),
        DYNAMIC_COLOR("dynamic_color"),
        SHOW_TOASTS("show_toasts"),
        SHIZUKU_AUTO_MANAGE("shizuku_auto_manage"),
        SHIZUKU_START_ON_RECORD("shizuku_start_on_record"),
        SHIZUKU_KEEP_ALIVE("shizuku_keep_alive"),
        SHIZUKU_AUTH_KEY("shizuku_auth_key"),
        ACCENT_COLOR("accent_color");
    }

    enum class IgnoreContactsMode(val key: String) {
        NONE("none"), ALL("all"), SELECTED("selected");
        companion object {
            fun fromKey(key: String?): IgnoreContactsMode =
                entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown IgnoreContactsMode key: $key")
        }
    }

    enum class ThemeMode(val key: String) {
        SYSTEM("system"), LIGHT("light"), DARK("dark"), WHITE("white"), BLACK("black"), AUTO_WB("auto_wb");
        companion object {
            fun fromKey(key: String?): ThemeMode =
                entries.firstOrNull { it.key == key } ?: throw IllegalArgumentException("Unknown ThemeMode key: $key")
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getBoolean(key: Key, default: Boolean = false) = prefs.getBoolean(key.id, default)
    private fun setBoolean(key: Key, value: Boolean) = prefs.edit { putBoolean(key.id, value) }
    private fun getString(key: Key, default: String? = null) = prefs.getString(key.id, default)
    private fun setString(key: Key, value: String?) = prefs.edit { putString(key.id, value) }
    private fun getInt(key: Key, default: Int = 0) = prefs.getInt(key.id, default)
    private fun setInt(key: Key, value: Int) = prefs.edit { putInt(key.id, value) }
    private fun getStringSet(key: Key, default: Set<String> = emptySet()) = prefs.getStringSet(key.id, default)?.toSet().orEmpty()
    private fun setStringSet(key: Key, value: Set<String>) = prefs.edit { putStringSet(key.id, value) }

    fun isDisclaimerAccepted() = getBoolean(Key.DISCLAIMER_ACCEPTED, DefaultsValue.DISCLAIMER_ACCEPTED)
    fun setDisclaimerAccepted(accepted: Boolean) = setBoolean(Key.DISCLAIMER_ACCEPTED, accepted)
    fun getRecordingFolderUri(): Uri? = getString(Key.RECORDING_FOLDER_URI, DefaultsValue.RECORDING_FOLDER_URI)?.toUri()
    fun setRecordingFolderUri(uri: Uri?) = setString(Key.RECORDING_FOLDER_URI, uri?.toString())
    fun isVibrationEnabled() = getBoolean(Key.VIBRATION_ENABLED, DefaultsValue.VIBRATION_ENABLED)
    fun setVibrationEnabled(enabled: Boolean) = setBoolean(Key.VIBRATION_ENABLED, enabled)
    fun isAutoRecordIncomingEnabled() = getBoolean(Key.AUTO_RECORD_INCOMING, DefaultsValue.AUTO_RECORD_INCOMING)
    fun setAutoRecordIncomingEnabled(enabled: Boolean) = setBoolean(Key.AUTO_RECORD_INCOMING, enabled)
    fun isAutoRecordOutgoingEnabled() = getBoolean(Key.AUTO_RECORD_OUTGOING, DefaultsValue.AUTO_RECORD_OUTGOING)
    fun setAutoRecordOutgoingEnabled(enabled: Boolean) = setBoolean(Key.AUTO_RECORD_OUTGOING, enabled)
    fun isRecordOnAnswerEnabled() = getBoolean(Key.RECORD_ON_ANSWER, DefaultsValue.RECORD_ON_ANSWER)
    fun setRecordOnAnswerEnabled(enabled: Boolean) = setBoolean(Key.RECORD_ON_ANSWER, enabled)
    fun isIgnoreAnonymousIncomingEnabled() = getBoolean(Key.IGNORE_ANONYMOUS_INCOMING, DefaultsValue.IGNORE_ANONYMOUS_INCOMING)
    fun setIgnoreAnonymousIncomingEnabled(enabled: Boolean) = setBoolean(Key.IGNORE_ANONYMOUS_INCOMING, enabled)
    fun isIgnoreCrossCountryIncomingEnabled() = getBoolean(Key.IGNORE_CROSS_COUNTRY_INCOMING, DefaultsValue.IGNORE_CROSS_COUNTRY_INCOMING)
    fun setIgnoreCrossCountryIncomingEnabled(enabled: Boolean) = setBoolean(Key.IGNORE_CROSS_COUNTRY_INCOMING, enabled)
    fun isIgnoreCrossCountryOutgoingEnabled() = getBoolean(Key.IGNORE_CROSS_COUNTRY_OUTGOING, DefaultsValue.IGNORE_CROSS_COUNTRY_OUTGOING)
    fun setIgnoreCrossCountryOutgoingEnabled(enabled: Boolean) = setBoolean(Key.IGNORE_CROSS_COUNTRY_OUTGOING, enabled)
    fun getIgnoreContactsModeIncoming() = IgnoreContactsMode.fromKey(getString(Key.IGNORE_CONTACTS_MODE_INCOMING, DefaultsValue.IGNORE_CONTACTS_MODE_INCOMING.key))
    fun setIgnoreContactsModeIncoming(mode: IgnoreContactsMode) = setString(Key.IGNORE_CONTACTS_MODE_INCOMING, mode.key)
    fun getIgnoreContactsModeOutgoing() = IgnoreContactsMode.fromKey(getString(Key.IGNORE_CONTACTS_MODE_OUTGOING, DefaultsValue.IGNORE_CONTACTS_MODE_OUTGOING.key))
    fun setIgnoreContactsModeOutgoing(mode: IgnoreContactsMode) = setString(Key.IGNORE_CONTACTS_MODE_OUTGOING, mode.key)
    fun getIgnoredContactsIncoming() = getStringSet(Key.IGNORED_CONTACTS_INCOMING, DefaultsValue.IGNORED_CONTACTS_INCOMING)
    fun setIgnoredContactsIncoming(numbers: Set<String>) = setStringSet(Key.IGNORED_CONTACTS_INCOMING, numbers)
    fun getIgnoredContactsOutgoing() = getStringSet(Key.IGNORED_CONTACTS_OUTGOING, DefaultsValue.IGNORED_CONTACTS_OUTGOING)
    fun setIgnoredContactsOutgoing(numbers: Set<String>) = setStringSet(Key.IGNORED_CONTACTS_OUTGOING, numbers)
    fun isLoggingEnabled() = getBoolean(Key.LOGGING_ENABLED, DefaultsValue.LOGGING_ENABLED)
    fun setLoggingEnabled(enabled: Boolean) = setBoolean(Key.LOGGING_ENABLED, enabled)
    fun isDebugEnabled() = getBoolean(Key.DEBUG_ENABLED, DefaultsValue.DEBUG_ENABLED)
    fun setDebugEnabled(enabled: Boolean) = setBoolean(Key.DEBUG_ENABLED, enabled)
    fun getDebugCallerNumber() = getString(Key.DEBUG_CALLER_NUMBER, DefaultsValue.DEBUG_CALLER_NUMBER) ?: DefaultsValue.DEBUG_CALLER_NUMBER
    fun setDebugCallerNumber(number: String) = setString(Key.DEBUG_CALLER_NUMBER, number)
    fun getAudioSource() = getString(Key.AUDIO_SOURCE, DefaultsValue.AUDIO_SOURCE) ?: DefaultsValue.AUDIO_SOURCE
    fun setAudioSource(source: String) = setString(Key.AUDIO_SOURCE, source)
    fun getAudioCodec() = getString(Key.AUDIO_CODEC, DefaultsValue.AUDIO_CODEC) ?: DefaultsValue.AUDIO_CODEC
    fun setAudioCodec(codec: String) = setString(Key.AUDIO_CODEC, codec)
    fun getAudioBitRate() = getInt(Key.AUDIO_BITRATE, DefaultsValue.AUDIO_BITRATE)
    fun setAudioBitRate(bitRate: Int) = setInt(Key.AUDIO_BITRATE, bitRate)
    fun getFileNameTemplate() = getString(Key.FILE_NAME_TEMPLATE, DefaultsValue.FILE_NAME_TEMPLATE) ?: DefaultsValue.FILE_NAME_TEMPLATE
    fun setFileNameTemplate(template: String) = setString(Key.FILE_NAME_TEMPLATE, template)
    fun getThemeMode() = ThemeMode.fromKey(getString(Key.THEME_MODE, DefaultsValue.THEME_MODE.key))
    fun setThemeMode(mode: ThemeMode) = setString(Key.THEME_MODE, mode.key)
    fun isDynamicColorEnabled() = getBoolean(Key.DYNAMIC_COLOR, DefaultsValue.DYNAMIC_COLOR)
    fun setDynamicColorEnabled(enabled: Boolean) = setBoolean(Key.DYNAMIC_COLOR, enabled)
    fun isShowToastsEnabled() = getBoolean(Key.SHOW_TOASTS, DefaultsValue.SHOW_TOASTS)
    fun setShowToastsEnabled(enabled: Boolean) = setBoolean(Key.SHOW_TOASTS, enabled)
    fun isShizukuAutoManageEnabled() = getBoolean(Key.SHIZUKU_AUTO_MANAGE, DefaultsValue.SHIZUKU_AUTO_MANAGE)
    fun setShizukuAutoManageEnabled(enabled: Boolean) = setBoolean(Key.SHIZUKU_AUTO_MANAGE, enabled)
    fun isShizukuStartOnRecordEnabled() = getBoolean(Key.SHIZUKU_START_ON_RECORD, DefaultsValue.SHIZUKU_START_ON_RECORD)
    fun setShizukuStartOnRecordEnabled(enabled: Boolean) = setBoolean(Key.SHIZUKU_START_ON_RECORD, enabled)
    fun isShizukuKeepAliveEnabled() = getBoolean(Key.SHIZUKU_KEEP_ALIVE, DefaultsValue.SHIZUKU_KEEP_ALIVE)
    fun setShizukuKeepAliveEnabled(enabled: Boolean) = setBoolean(Key.SHIZUKU_KEEP_ALIVE, enabled)
    fun getShizukuAuthKey() = getString(Key.SHIZUKU_AUTH_KEY, DefaultsValue.SHIZUKU_AUTH_KEY) ?: DefaultsValue.SHIZUKU_AUTH_KEY
    fun setShizukuAuthKey(key: String) = setString(Key.SHIZUKU_AUTH_KEY, key)
    /** Returns the custom accent color as ARGB-packed Int (used when dynamic color is disabled). */
    fun getAccentColor(): Int = getInt(Key.ACCENT_COLOR, DefaultsValue.ACCENT_COLOR)
    /** Stores the custom accent color as ARGB-packed Int. */
    fun setAccentColor(argb: Int) = setInt(Key.ACCENT_COLOR, argb)
}
