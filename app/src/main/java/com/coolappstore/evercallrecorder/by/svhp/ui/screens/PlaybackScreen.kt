package com.coolappstore.evercallrecorder.by.svhp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.PlaybackViewModel
import com.coolappstore.evercallrecorder.by.svhp.ui.viewmodels.RecordingItem
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    recording: RecordingItem,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: PlaybackViewModel = viewModel()
    val isPlaying by vm.isPlaying.collectAsState()
    val position by vm.currentPosition.collectAsState()
    val duration by vm.duration.collectAsState()
    val note by vm.note.collectAsState()

    LaunchedEffect(recording.uri) { vm.load(recording.uri) }

    DisposableEffect(Unit) { onDispose { vm.player.pause() } }

    BackHandler { onBack() }

    val title = recording.contactName ?: recording.phoneNumber
    val subtitle = recording.contactName?.let { recording.phoneNumber } ?: ""
    val dateStr = recording.date?.let {
        SimpleDateFormat("MMMM d, yyyy • hh:mm a", Locale.getDefault()).format(it)
    } ?: ""
    val isIncoming = recording.direction == "in"
    val accentColor = if (isIncoming)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.tertiary

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Recording", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Call info card ────────────────────────────────────────────────
                Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (dateStr.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isIncoming) Icons.Rounded.CallReceived else Icons.Rounded.CallMade,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isIncoming) "Incoming" else "Outgoing",
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // ── Player card ───────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Waveform placeholder / pulsing indicator
                    PlayerVisualizer(isPlaying = isPlaying, accentColor = accentColor)

                    // Time labels + seek slider
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        var isSeeking by remember { mutableStateOf(false) }
                        var seekValue by remember { mutableFloatStateOf(0f) }

                        val sliderPosition = if (isSeeking) seekValue
                            else if (duration > 0) position / duration.toFloat()
                            else 0f

                        Slider(
                            value = sliderPosition.coerceIn(0f, 1f),
                            onValueChange = { isSeeking = true; seekValue = it },
                            onValueChangeFinished = {
                                vm.seekTo((seekValue * duration).toLong())
                                isSeeking = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = accentColor.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatMs(position),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatMs(duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip back 5s
                        FilledTonalIconButton(
                            onClick = { vm.seekBack() },
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Icon(Icons.Rounded.Replay5, contentDescription = "Back 5s",
                                modifier = Modifier.size(26.dp))
                        }

                        // Play / Pause
                        FilledIconButton(
                            onClick = { vm.togglePlayPause() },
                            modifier = Modifier.size(68.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = accentColor
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }

                        // Skip forward 5s
                        FilledTonalIconButton(
                            onClick = { vm.seekForward() },
                            modifier = Modifier.size(52.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Icon(Icons.Rounded.Forward5, contentDescription = "Forward 5s",
                                modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }

            // ── Notes card ────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { vm.updateNote(it) },
                        placeholder = {
                            Text(
                                "Add notes about this call…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlayerVisualizer(isPlaying: Boolean, accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "viz")
    val barCount = 24
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
    ) {
        repeat(barCount) { index ->
            val animatedHeight by if (isPlaying) {
                infiniteTransition.animateFloat(
                    initialValue = 0.15f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 300 + (index % 6) * 80,
                            easing = FastOutSlowInEasing
                        ),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar$index"
                )
            } else {
                remember { mutableFloatStateOf(0.25f) }
            }
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(animatedHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accentColor.copy(alpha = 0.9f),
                                accentColor.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val mins = TimeUnit.MILLISECONDS.toMinutes(ms)
    val secs = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(mins, secs)
}
