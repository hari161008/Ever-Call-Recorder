package com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coolappstore.evercallrecorder.by.svhp.data.AppPreferences
import com.coolappstore.evercallrecorder.by.svhp.system.permissions.PermissionChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordingItem(
    val uri: Uri,
    val displayName: String,
    val phoneNumber: String,
    val contactName: String?,
    val direction: String,
    val date: Date?,
    val sizeBytes: Long,
    val extension: String,
    val isFavourite: Boolean = false
)

enum class SortField { DATE, NAME, TIME }
enum class SortOrder { ASC, DESC }

data class SortConfig(
    val field: SortField = SortField.TIME,
    val order: SortOrder = SortOrder.ASC
)

enum class FilterTab { ALL, FAVOURITES }

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = AppPreferences(application)
    private val favPrefs = application.getSharedPreferences("home_favourites", Context.MODE_PRIVATE)
    private val notesPrefs = application.getSharedPreferences("recording_notes", Context.MODE_PRIVATE)

    private val _allRecordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val sortConfig = MutableStateFlow(SortConfig())
    val filterTab = MutableStateFlow(FilterTab.ALL)
    val searchQuery = MutableStateFlow("")
    val recordings = MutableStateFlow<List<RecordingItem>>(emptyList())

    // Date formats the app can produce
    private val dateFormats = listOf(
        SimpleDateFormat("yyyyMMdd_HHmmss.SSSZ", Locale.CANADA),
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CANADA)
    )

    init {
        loadRecordings()
        viewModelScope.launch { sortConfig.collect { applyFilters() } }
        viewModelScope.launch { filterTab.collect { applyFilters() } }
        viewModelScope.launch { searchQuery.collect { applyFilters() } }
    }

    fun refresh() {
        if (!_isLoading.value) loadRecordings()
    }

    fun toggleFavourite(item: RecordingItem) {
        val key = item.uri.toString()
        val isFav = favPrefs.getBoolean(key, false)
        favPrefs.edit().putBoolean(key, !isFav).apply()
        _allRecordings.value = _allRecordings.value.map {
            if (it.uri == item.uri) it.copy(isFavourite = !isFav) else it
        }
        applyFilters()
    }

    fun getNote(uri: Uri): String = notesPrefs.getString(uri.toString(), "") ?: ""
    fun saveNote(uri: Uri, note: String) = notesPrefs.edit().putString(uri.toString(), note).apply()

    private fun isFavourite(uri: Uri) = favPrefs.getBoolean(uri.toString(), false)

    private fun loadRecordings() {
        viewModelScope.launch {
            _isLoading.value = true
            _allRecordings.value = fetchRecordings()
            applyFilters()
            _isLoading.value = false
        }
    }

    private fun applyFilters() {
        val query = searchQuery.value.trim().lowercase()
        val tab = filterTab.value
        val sort = sortConfig.value
        var list = _allRecordings.value
        if (query.isNotEmpty()) {
            list = list.filter {
                it.phoneNumber.lowercase().contains(query) ||
                it.displayName.lowercase().contains(query) ||
                (it.contactName?.lowercase()?.contains(query) == true)
            }
        }
        if (tab == FilterTab.FAVOURITES) list = list.filter { it.isFavourite }
        list = when (sort.field) {
            SortField.DATE, SortField.TIME -> list.sortedBy { it.date?.time ?: 0L }
            SortField.NAME -> list.sortedBy { (it.contactName ?: it.phoneNumber).lowercase() }
        }
        if (sort.order == SortOrder.DESC) list = list.reversed()
        recordings.value = list
    }

    private suspend fun fetchRecordings(): List<RecordingItem> = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val folderUri = preferences.getRecordingFolderUri() ?: return@withContext emptyList()
        val directory = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        if (!directory.exists() || !directory.canRead()) return@withContext emptyList()
        val hasContacts = PermissionChecks.hasContactsPermission(context)

        directory.listFiles()
            .filter { it.isFile && it.name != null }
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                val ext = name.substringAfterLast('.', "")
                // Strip extension for parsing
                val baseName = name.substringBeforeLast('.')
                // Format: {date}_{direction}_{phone_number}
                // date = yyyyMMdd_HHmmss.SSSZ  → parts[0]_parts[1]
                // direction = parts[2]  (in/out)
                // phone = parts[3..] joined (numbers with + or - don't contain _)
                val parts = baseName.split("_")
                val direction = if (parts.size >= 3) parts[2] else ""
                // Phone number: everything after direction token
                val phoneRaw = if (parts.size >= 4) parts.drop(3).joinToString("_") else ""
                // Date: first two tokens
                val dateRaw = if (parts.size >= 2) "${parts[0]}_${parts[1]}" else ""
                val date = parseDate(dateRaw)
                val phoneNumber = phoneRaw.trim().ifBlank { "Unknown" }
                val contactName = if (hasContacts && phoneNumber != "Unknown")
                    resolveContactName(context, phoneNumber) else null

                RecordingItem(
                    uri = file.uri,
                    displayName = name,
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    direction = direction,
                    date = date,
                    sizeBytes = file.length(),
                    extension = ext,
                    isFavourite = isFavourite(file.uri)
                )
            }
    }

    private fun parseDate(raw: String): Date? {
        for (fmt in dateFormats) {
            try { return fmt.parse(raw) } catch (_: Exception) {}
        }
        return null
    }

    private fun resolveContactName(context: Context, phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst())
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                else null
            }
        } catch (_: Exception) { null }
    }
}
