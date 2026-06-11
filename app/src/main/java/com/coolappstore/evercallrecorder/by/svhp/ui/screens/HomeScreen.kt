package com.coolappstore.evercallrecorder.by.svhp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    appVersion: String,
    onSettingsClick: () -> Unit,
    onRecordingClick: (com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.RecordingItem, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val vm: HomeViewModel = viewModel()
    val recordings by vm.recordings.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val filterTab by vm.filterTab.collectAsState()
    val sortConfig by vm.sortConfig.collectAsState()

    // When search is active, first back press clears search instead of exiting
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = query.isNotBlank()) {
        vm.searchQuery.value = ""
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Ever Call Recorder", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        val grouped: Map<String, List<com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.RecordingItem>> = remember(recordings) {
            recordings.groupBy { groupLabel(it.date) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                SearchBar(
                    query = query,
                    onQueryChange = { vm.searchQuery.value = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                FilterPillRow(
                    filterTab = filterTab,
                    sortConfig = sortConfig,
                    onFilterChange = { vm.filterTab.value = it },
                    onSortChange = { vm.sortConfig.value = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            when {
                isLoading -> item {
                    Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                recordings.isEmpty() -> item {
                    EmptyState(isFavourites = filterTab == FilterTab.FAVOURITES, hasQuery = query.isNotBlank())
                }
                else -> {
                    grouped.forEach { (dateLabel, items) ->
                        item(key = "header_$dateLabel") {
                            DateGroupHeader(label = dateLabel, modifier = Modifier.animateItem(fadeInSpec = tween(340), placementSpec = spring(stiffness = Spring.StiffnessLow), fadeOutSpec = tween(220)))
                        }
                        item(key = "group_$dateLabel") {
                            RecordingGroupCard(
                                items = items,
                                searchQuery = query,
                                onFavouriteToggle = { vm.toggleFavourite(it) },
                                onRecordingClick = { recording -> onRecordingClick(recording, query) },
                                modifier = Modifier.animateItem(fadeInSpec = tween(380, easing = FastOutSlowInEasing), placementSpec = spring(stiffness = Spring.StiffnessLow), fadeOutSpec = tween(240))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Search bar ────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search recordings or notes…", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    )
}

// ── Filter + Sort pills ────────────────────────────────────────────────────────

@Composable
private fun FilterPillRow(
    filterTab: FilterTab,
    sortConfig: SortConfig,
    onFilterChange: (FilterTab) -> Unit,
    onSortChange: (SortConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterPill(label = "All", selected = filterTab == FilterTab.ALL, icon = Icons.Rounded.List, onClick = { onFilterChange(FilterTab.ALL) })
        FilterPill(
            label = "Favourites",
            selected = filterTab == FilterTab.FAVOURITES,
            icon = if (filterTab == FilterTab.FAVOURITES) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
            onClick = { onFilterChange(FilterTab.FAVOURITES) }
        )
        Spacer(Modifier.weight(1f))
        Box {
            val sortLabel = when (sortConfig.field) {
                SortField.DATE, SortField.TIME -> "Time"
                SortField.NAME -> "Name"
            }
            val sortIcon: ImageVector = if (sortConfig.order == SortOrder.ASC) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward
            FilterPill(label = sortLabel, selected = true, icon = sortIcon, trailingIcon = Icons.Rounded.UnfoldMore, onClick = { sortMenuExpanded = true })
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                SortOption.entries.forEach { option ->
                    val isSelected = sortConfig.field == option.field && sortConfig.order == option.order
                    DropdownMenuItem(
                        text = {
                            Text(option.label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        },
                        leadingIcon = {
                            Icon(option.icon, contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        },
                        onClick = { onSortChange(SortConfig(field = option.field, order = option.order)); sortMenuExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, icon: ImageVector, trailingIcon: ImageVector? = null, onClick: () -> Unit) {
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, shape = CircleShape, color = containerColor, contentColor = contentColor, tonalElevation = 0.dp) {
        Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Sort options (DATE removed per request) ───────────────────────────────────

private enum class SortOption(val label: String, val field: SortField, val order: SortOrder, val icon: ImageVector) {
    TIME_DESC("Time — Newest first", SortField.TIME, SortOrder.DESC, Icons.Rounded.ArrowDownward),
    TIME_ASC("Time — Oldest first",  SortField.TIME, SortOrder.ASC,  Icons.Rounded.ArrowUpward),
    NAME_ASC("Name — A to Z",        SortField.NAME, SortOrder.ASC,  Icons.Rounded.ArrowUpward),
    NAME_DESC("Name — Z to A",       SortField.NAME, SortOrder.DESC, Icons.Rounded.ArrowDownward),
}

// ── Date group header ─────────────────────────────────────────────────────────

@Composable
private fun DateGroupHeader(label: String, modifier: Modifier = Modifier) {
    Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp))
}

// ── Recording group card ──────────────────────────────────────────────────────

@Composable
private fun RecordingGroupCard(
    items: List<com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.RecordingItem>,
    searchQuery: String,
    onFavouriteToggle: (com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.RecordingItem) -> Unit,
    onRecordingClick: (com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.RecordingItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            items.forEachIndexed { index, item ->
                RecordingRow(
                    item = item,
                    searchQuery = searchQuery,
                    onFavouriteToggle = { onFavouriteToggle(item) },
                    onClick = { onRecordingClick(item) }
                )
                if (index < items.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

// ── Single recording row ──────────────────────────────────────────────────────

@Composable
private fun RecordingRow(
    item: com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.RecordingItem,
    searchQuery: String,
    onFavouriteToggle: () -> Unit,
    onClick: () -> Unit
) {
    val vm: HomeViewModel = viewModel()
    val context = LocalContext.current
    val isIncoming = item.direction == "in"
    val accentColor = if (isIncoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val directionIcon = if (isIncoming) Icons.Rounded.CallReceived else Icons.Rounded.CallMade
    val directionLabel = if (isIncoming) "Incoming" else "Outgoing"
    val timeStr = item.date?.let { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it) } ?: ""
    val displayName = item.contactName ?: item.phoneNumber

    // Load contact photo async
    var photoBitmap by remember(item.phoneNumber) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.phoneNumber) {
        photoBitmap = vm.loadContactPhoto(context, item.phoneNumber)
    }

    // Build note snippet if search matched note
    val lowerQuery = searchQuery.trim().lowercase()
    val noteSnippet: String? = remember(item.noteText, lowerQuery) {
        if (lowerQuery.isNotBlank() && item.noteText.lowercase().contains(lowerQuery)) {
            buildNoteSnippet(item.noteText, lowerQuery)
        } else null
    }

    ListItem(
        modifier = Modifier.clickable { onClick() },
        leadingContent = {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initial = item.contactName?.firstOrNull()?.uppercaseChar()?.toString()
                        ?: item.phoneNumber.firstOrNull { it.isDigit() }?.toString()
                        ?: "?"
                    Text(initial, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accentColor)
                }
            }
        },
        headlineContent = {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = directionLabel, style = MaterialTheme.typography.labelSmall, color = accentColor)
                    if (timeStr.isNotBlank()) {
                        Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (item.sizeBytes > 0) {
                        Text("·", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatSize(item.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Note snippet when search matches note
                if (noteSnippet != null) {
                    val highlightColor = MaterialTheme.colorScheme.primary
                    val annotated = buildAnnotatedString {
                        val lower = noteSnippet.lowercase()
                        var start = 0
                        while (true) {
                            val idx = lower.indexOf(lowerQuery, start)
                            if (idx == -1) {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(noteSnippet.substring(start)) }
                                break
                            }
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(noteSnippet.substring(start, idx)) }
                            withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold, background = highlightColor.copy(alpha = 0.15f))) {
                                append(noteSnippet.substring(idx, idx + lowerQuery.length))
                            }
                            start = idx + lowerQuery.length
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        Text(annotated, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onFavouriteToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (item.isFavourite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (item.isFavourite) "Unfavourite" else "Favourite",
                    tint = if (item.isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(isFavourites: Boolean, hasQuery: Boolean) {
    val icon = when { hasQuery -> Icons.Outlined.SearchOff; isFavourites -> Icons.Outlined.FavoriteBorder; else -> Icons.Outlined.MicNone }
    val title = when { hasQuery -> "No results found"; isFavourites -> "No favourites yet"; else -> "No recordings yet" }
    val body = when {
        hasQuery -> "Try a different search term."
        isFavourites -> "Tap the heart icon on any recording to save it here."
        else -> "Recordings will appear here once calls are captured."
    }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            }
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun buildNoteSnippet(noteText: String, query: String): String {
    val idx = noteText.lowercase().indexOf(query.lowercase())
    if (idx == -1) return noteText.take(80)
    val start = (idx - 20).coerceAtLeast(0)
    val end = (idx + query.length + 40).coerceAtMost(noteText.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < noteText.length) "…" else ""
    return "$prefix${noteText.substring(start, end)}$suffix"
}

private fun groupLabel(date: Date?): String {
    if (date == null) return "Unknown date"
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { time = date }
    return when {
        isSameDay(now, cal) -> "Today"
        isYesterday(now, cal) -> "Yesterday"
        isSameWeek(now, cal) -> SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
        isSameYear(now, cal) -> SimpleDateFormat("MMMM d", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    }
}

private fun isSameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
private fun isYesterday(now: Calendar, b: Calendar): Boolean { val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }; return isSameDay(yesterday, b) }
private fun isSameWeek(now: Calendar, b: Calendar) = now.get(Calendar.YEAR) == b.get(Calendar.YEAR) && now.get(Calendar.WEEK_OF_YEAR) == b.get(Calendar.WEEK_OF_YEAR)
private fun isSameYear(now: Calendar, b: Calendar) = now.get(Calendar.YEAR) == b.get(Calendar.YEAR)
private fun formatSize(bytes: Long): String = when { bytes < 1024 -> "${bytes}B"; bytes < 1024 * 1024 -> "${bytes / 1024}KB"; else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}MB" }
