package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AmbientSoundType
import com.example.data.model.ThemePreference
import com.example.ui.components.PomofocusAuthSheet
import com.example.ui.theme.FocusColorLight
import com.example.ui.theme.focusAccentColor
import com.example.viewmodel.PomodoroViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: PomodoroViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val syncState by viewModel.pomofocusSyncState.collectAsStateWithLifecycle()
    val accentColor = focusAccentColor()
    var showResetDialog by remember { mutableStateOf(false) }
    var showAuthSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        item {
            // Header
            Column {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Customize theme, timers and soundscapes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Theme / Appearance Selector
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Appearance & Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemePreference.values().forEach { pref ->
                            val isSelected = settings.themePreference == pref
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) FocusColorLight else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.updateSettings(settings.copy(themePreference = pref))
                                    }
                            ) {
                                Text(
                                    text = pref.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Keep Screen Awake",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Prevent phone screen from turning off while timer runs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.keepScreenAwake,
                            onCheckedChange = {
                                viewModel.updateSettings(settings.copy(keepScreenAwake = it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = FocusColorLight,
                                checkedTrackColor = accentColor.copy(alpha = 0.45f)
                            )
                        )
                    }
                }
            }
        }

        // Pomofocus Cloud Sync Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_pomofocus_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = if (syncState.isConnected) accentColor.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(36.dp)
                            ) {
                                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (syncState.isConnected) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                        contentDescription = "Pomofocus Sync",
                                        tint = if (syncState.isConnected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Pomofocus.io Cloud Sync",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (syncState.isConnected) "Connected: ${syncState.userEmail ?: "Active Account"}" else "Not connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (syncState.isConnected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (syncState.isConnected) {
                            IconButton(
                                onClick = { viewModel.syncPomofocusNow() },
                                enabled = !syncState.isSyncing
                            ) {
                                if (syncState.isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accentColor)
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync Now", tint = accentColor)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (syncState.isConnected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-sync on Session Complete",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Automatically syncs tasks whenever a Pomodoro finishes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = syncState.autoSyncEnabled,
                                onCheckedChange = { viewModel.setPomofocusAutoSync(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = FocusColorLight, checkedTrackColor = accentColor.copy(alpha = 0.45f))
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { viewModel.disconnectPomofocus() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.LinkOff, contentDescription = "Disconnect", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disconnect Account", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Text(
                            text = "Log into your Pomofocus.io account to synchronize your tasks, estimated pomodoros, and completion progress across all devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showAuthSheet = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect Pomofocus Account", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }


        // Timer Durations
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Outlined.Timer, contentDescription = "Durations", tint = accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Timer Durations & Goals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Work Duration
                    DurationSliderItem(
                        label = "Focus Interval",
                        valueMinutes = settings.workDurationMinutes,
                        range = 5f..90f,
                        accentColor = accentColor,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(workDurationMinutes = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Short Break Duration
                    DurationSliderItem(
                        label = "Short Break Interval",
                        valueMinutes = settings.shortBreakDurationMinutes,
                        range = 1f..30f,
                        accentColor = accentColor,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(shortBreakDurationMinutes = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Long Break Duration
                    DurationSliderItem(
                        label = "Long Break Interval",
                        valueMinutes = settings.longBreakDurationMinutes,
                        range = 5f..60f,
                        accentColor = accentColor,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(longBreakDurationMinutes = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Long Break Interval Rounds
                    DurationSliderItem(
                        label = "Rounds before Long Break",
                        valueMinutes = settings.roundsBeforeLongBreak,
                        unit = "rounds",
                        range = 2f..8f,
                        accentColor = accentColor,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(roundsBeforeLongBreak = it))
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Daily Focus Goal
                    DurationSliderItem(
                        label = "Daily Focus Goal",
                        valueMinutes = settings.dailyGoalMinutes,
                        unit = "min",
                        stepIncrement = 15,
                        range = 30f..360f,
                        accentColor = accentColor,
                        onValueChange = {
                            viewModel.updateSettings(settings.copy(dailyGoalMinutes = it))
                        }
                    )
                }
            }
        }

        // Sound & Vibration Settings
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Outlined.NotificationsActive, contentDescription = "Audio", tint = accentColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Sound & Atmosphere", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Chime Alert
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Completion Chime", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(text = "Resonant Tibetan singing bowl bell", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.soundAlertEnabled,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(soundAlertEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = FocusColorLight, checkedTrackColor = accentColor.copy(alpha = 0.45f))
                        )
                    }

                    // Test chime button
                    OutlinedButton(
                        onClick = { viewModel.audioEngine.playChime() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .testTag("test_chime_button")
                    ) {
                        Text("🔔 Test Zen Singing Bowl Chime", color = accentColor)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Vibration Alert
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Gentle Vibration", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(text = "Rhythmic haptic pulse on finish", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.vibrationAlertEnabled,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(vibrationAlertEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = FocusColorLight, checkedTrackColor = accentColor.copy(alpha = 0.45f))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Ticking sound
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Subtle Clock Ticking", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(text = "Rhythmic mechanical tick during focus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.tickingSound,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(tickingSound = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = FocusColorLight, checkedTrackColor = accentColor.copy(alpha = 0.45f))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Ambient Sound Default
                    Text(text = "Default Focus Soundscape", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    AmbientSoundType.values().forEach { type ->
                        val isSelected = settings.ambientSoundType == type
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.setAmbientSound(type)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = type.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = type.iconDescription,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Automation Settings
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "Automation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Auto-start Breaks", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(text = "Automatically countdown break when focus ends", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.autoStartBreaks,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(autoStartBreaks = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = FocusColorLight, checkedTrackColor = accentColor.copy(alpha = 0.45f))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Auto-start Focus", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(text = "Automatically start next focus when break ends", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.autoStartWork,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(autoStartWork = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = FocusColorLight, checkedTrackColor = accentColor.copy(alpha = 0.45f))
                        )
                    }
                }
            }
        }


        // Data Reset & About
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "Data & Defaults", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Restore default timer durations or clear past session history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.resetSettingsToDefaults() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Outlined.RestartAlt, contentDescription = "Defaults", modifier = Modifier.size(16.dp), tint = accentColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Defaults", color = accentColor, style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Outlined.Restore, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear History", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "MuktoFlow v2.1",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = "“Peace comes from within. Do not seek it without.” — Focus deeply, rest mindfully.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Clear Session History?") },
            text = { Text("This will reset all your logged focus sessions and streak metrics. Your tasks will not be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistory()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAuthSheet) {
        PomofocusAuthSheet(viewModel = viewModel, onDismiss = { showAuthSheet = false })
    }
}

@Composable
fun DurationSliderItem(
    label: String,
    valueMinutes: Int,
    range: ClosedFloatingPointRange<Float>,
    unit: String = "min",
    stepIncrement: Int = 1,
    accentColor: Color = FocusColorLight,
    onValueChange: (Int) -> Unit
) {
    val minVal = range.start.roundToInt()
    val maxVal = range.endInclusive.roundToInt()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            val next = (valueMinutes - stepIncrement).coerceAtLeast(minVal)
                            onValueChange(next)
                        }
                ) {
                    Text("-", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$valueMinutes $unit",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(10.dp))
                androidx.compose.foundation.layout.Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            val next = (valueMinutes + stepIncrement).coerceAtMost(maxVal)
                            onValueChange(next)
                        }
                ) {
                    Text("+", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        Slider(
            value = valueMinutes.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(minVal, maxVal)) },
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = FocusColorLight,
                activeTrackColor = accentColor
            )
        )
    }
}
