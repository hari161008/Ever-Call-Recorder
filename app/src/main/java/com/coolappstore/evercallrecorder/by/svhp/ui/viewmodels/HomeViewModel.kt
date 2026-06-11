package com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.coolappstore.evercallrecorder.by.svhp.data.AppPreferences
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
    val isFavourite: Boolean = false,
    val noteText: String = ""
)

// SortField.DATE kept for safe deserialization of old prefs, treated as TIME
enum class SortField { DATE, NAME, TIME }
enum class SortOrder { ASC, DESC }

data class SortConfig(
    val field: SortField = SortField.TIME,
    val order: SortOrder = SortOrder.DESC
)

enum class FilterTab { ALL, FAVOURITES }

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = AppPreferences(application)
    private val favPrefs   = application.getSharedPreferences("home_favourites",  Context.MODE_PRIVATE)
    private val notesPrefs = application.getSharedPreferences("recording_notes",  Context.MODE_PRIVATE)
    private val sortPrefs  = application.getSharedPreferences("sort_config",      Context.MODE_PRIVATE)

    private val _allRecordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    private val _isLoading     = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val sortConfig = MutableStateFlow(
        run {
            val raw = sortPrefs.getString("sort_field", SortField.TIME.name) ?: SortField.TIME.name
            // Gracefully handle old DATE value — treat as TIME
            val field = runCatching { SortField.valueOf(raw) }.getOrDefault(SortField.TIME)
                .let { if (it == SortField.DATE) SortField.TIME else it }
            SortConfig(
                field = field,
                order = SortOrder.valueOf(sortPrefs.getString("sort_order", SortOrder.DESC.name) ?: SortOrder.DESC.name)
            )
        }
    )

    val filterTab   = MutableStateFlow(FilterTab.ALL)
    val searchQuery = MutableStateFlow("")
    val recordings  = MutableStateFlow<List<RecordingItem>>(emptyList())

    private val dateFormats = listOf(
        SimpleDateFormat("yyyyMMdd_HHmmss.SSSZ", Locale.CANADA),
        SimpleDateFormat("yyyyMMdd_HHmmss",       Locale.CANADA)
    )

    init {
        loadRecordings()
        viewModelScope.launch {
            sortConfig.collect { config ->
                sortPrefs.edit()
                    .putString("sort_field", config.field.name)
                    .putString("sort_order", config.order.name)
                    .apply()
                applyFilters()
            }
        }
        viewModelScope.launch { filterTab.collect   { applyFilters() } }
        viewModelScope.launch { searchQuery.collect { applyFilters() } }
    }

    fun refresh() { if (!_isLoading.value) loadRecordings() }

    fun toggleFavourite(item: RecordingItem) {
        val key  = item.uri.toString()
        val isFav = favPrefs.getBoolean(key, false)
        favPrefs.edit().putBoolean(key, !isFav).apply()
        _allRecordings.value = _allRecordings.value.map {
            if (it.uri == item.uri) it.copy(isFavourite = !isFav) else it
        }
        applyFilters()
    }

    fun getNote(uri: Uri)                = notesPrefs.getString(uri.toString(), "") ?: ""
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
        val tab   = filterTab.value
        val sort  = sortConfig.value
        var list  = _allRecordings.value

        if (query.isNotEmpty()) {
            list = list.filter {
                it.phoneNumber.lowercase().contains(query) ||
                it.displayName.lowercase().contains(query) ||
                (it.contactName?.lowercase()?.contains(query) == true) ||
                it.noteText.lowercase().contains(query)
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
        val context   = getApplication<Application>()
        val folderUri = preferences.getRecordingFolderUri() ?: return@withContext emptyList()
        val dir = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext emptyList()
        if (!dir.exists() || !dir.canRead()) return@withContext emptyList()

        dir.listFiles()
            .filter { it.isFile && it.name != null }
            .mapNotNull { file ->
                val name     = file.name ?: return@mapNotNull null
                val ext      = name.substringAfterLast('.', "")
                val baseName = name.substringBeforeLast('.')
                val parts     = baseName.split("_")
                val direction = if (parts.size >= 3) parts[2] else ""
                val phoneRaw  = if (parts.size >= 4) parts.drop(3).joinToString("_") else ""
                val dateRaw   = if (parts.size >= 2) "${parts[0]}_${parts[1]}" else ""
                val date        = parseDate(dateRaw)
                val phoneNumber = phoneRaw.trim().ifBlank { "Unknown" }
                val contactName = if (phoneNumber != "Unknown") resolveContactName(context, phoneNumber) else null
                val noteText    = notesPrefs.getString(file.uri.toString(), "") ?: ""
                RecordingItem(
                    uri         = file.uri,
                    displayName = name,
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    direction   = direction,
                    date        = date,
                    sizeBytes   = file.length(),
                    extension   = ext,
                    isFavourite = isFavourite(file.uri),
                    noteText    = noteText
                )
            }
    }

    private fun parseDate(raw: String): Date? {
        for (fmt in dateFormats) { runCatching { return fmt.parse(raw) } }
        return null
    }

    private fun resolveContactName(context: Context, phoneNumber: String): String? {
        return try {
            val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, phoneNumber)
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: Exception) { null }
    }

    /** Loads contact photo as ImageBitmap, or null if unavailable. */
    suspend fun loadContactPhoto(context: Context, phoneNumber: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            try {
                val lookupUri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI, phoneNumber
                )
                context.contentResolver.query(
                    lookupUri,
                    arrayOf(ContactsContract.PhoneLookup.PHOTO_URI),
                    null, null, null
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@withContext null
                    val photoUriStr = cursor.getString(0) ?: return@withContext null
                    val stream = context.contentResolver.openInputStream(Uri.parse(photoUriStr))
                        ?: return@withContext null
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            } catch (_: Exception) { null }
        }
}
