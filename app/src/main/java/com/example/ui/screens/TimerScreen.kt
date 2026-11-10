package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AmbientSoundType
import com.example.data.model.TaskPriority
import com.example.data.model.ThemePreference
import com.example.data.model.TimerMode
import com.example.data.model.TimerStatus
import com.example.ui.theme.BambooSecondaryDark
import com.example.ui.theme.BambooSecondaryLight
import com.example.ui.theme.FocusColorDark
import com.example.ui.theme.FocusColorLight
import com.example.ui.theme.LongBreakColorDark
import com.example.ui.theme.LongBreakColorLight
import com.example.ui.theme.ShortBreakColorDark
import com.example.ui.theme.ShortBreakColorLight
import com.example.ui.theme.isZenDarkTheme
import com.example.viewmodel.PomodoroViewModel
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimerScreen(
    viewModel: PomodoroViewModel,
    onNavigateToTasks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mode by viewModel.timerMode.collectAsStateWithLifecycle()
    val status by viewModel.timerStatus.collectAsStateWithLifecycle()
    val secondsRemaining by viewModel.secondsRemaining.collectAsStateWithLifecycle()
    val totalSeconds by viewModel.totalSecondsForMode.collectAsStateWithLifecycle()
    val roundsInCycle by viewModel.completedRoundsInCycle.collectAsStateWithLifecycle()
    val activeTask by viewModel.activeTask.collectAsStateWithLifecycle()
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val streakDays by viewModel.streakDays.collectAsStateWithLifecycle()
    val todayFocusMinutes by viewModel.todayFocusMinutes.collectAsStateWithLifecycle()
    val ambientType by viewModel.currentAmbientType.collectAsStateWithLifecycle()
    val isAmbientPlaying by viewModel.isAmbientPlaying.collectAsStateWithLifecycle()
    val ambientVolume by viewModel.ambientVolume.collectAsStateWithLifecycle()
    val isZenFullscreen by viewModel.isZenFullscreen.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshLiveNotification()
        }
    }

    var showSoundSheet by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showQuickTaskSheet by remember { mutableStateOf(false) }

    val progress = if (totalSeconds > 0) {
        (secondsRemaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "timerProgress"
    )

    // Breathing glow animation only when timer is running
    val pulseScale: Float
    val auraAlpha: Float
    if (status == TimerStatus.RUNNING) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.035f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        val animatedAura by infiniteTransition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.19f,
            animationSpec = infiniteRepeatable(
                animation = tween(2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "auraAlpha"
        )
        pulseScale = animatedScale
        auraAlpha = animatedAura
    } else {
        pulseScale = 1.0f
        auraAlpha = 0.08f
    }

    val isDarkTheme = isZenDarkTheme()
    val primaryColor = when (mode) {
        TimerMode.WORK -> FocusColorLight
        TimerMode.SHORT_BREAK -> ShortBreakColorLight
        TimerMode.LONG_BREAK -> LongBreakColorLight
    }
    val accentContentColor = when (mode) {
        TimerMode.WORK -> if (isDarkTheme) FocusColorDark else FocusColorLight
        TimerMode.SHORT_BREAK -> if (isDarkTheme) ShortBreakColorDark else ShortBreakColorLight
        TimerMode.LONG_BREAK -> if (isDarkTheme) LongBreakColorDark else LongBreakColorLight
    }

    val secondaryAccent = if (!isDarkTheme) BambooSecondaryLight else BambooSecondaryDark

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = auraAlpha),
                        Color.Transparent
                    ),
                    radius = 900f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentContentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Spa,
                            contentDescription = "MuktoFlow Logo",
                            tint = accentContentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "MuktoFlow",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (isZenFullscreen) "Zen Lock Active" else "Mindful Focus Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isZenFullscreen) accentContentColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Zen Fullscreen Toggle Button
                    Surface(
                        shape = CircleShape,
                        color = if (isZenFullscreen) accentContentColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.toggleZenFullscreen() }
                            .testTag("zen_fullscreen_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isZenFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Zen Mode",
                                tint = accentContentColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Theme Quick-Toggle Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.cycleThemePreference() }
                            .testTag("theme_toggle_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (settings.themePreference) {
                                    ThemePreference.DARK -> Icons.Outlined.DarkMode
                                    ThemePreference.LIGHT -> Icons.Outlined.LightMode
                                    ThemePreference.SYSTEM -> Icons.Outlined.BrightnessAuto
                                },
                                contentDescription = "Switch Theme",
                                tint = accentContentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Streak Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${streakDays}d",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Mode Switcher Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(2.dp)
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        TimerMode.values().forEach { timerMode ->
                            val isSelected = mode == timerMode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) primaryColor else Color.Transparent
                                    )
                                    .clickable { viewModel.selectMode(timerMode) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .testTag("mode_${timerMode.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = timerMode.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Cycle Rounds Indicator
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                val totalRounds = settings.roundsBeforeLongBreak.coerceAtLeast(1)
                val currentInCycle = (roundsInCycle % totalRounds)

                Text(
                    text = "Round ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                for (i in 0 until totalRounds) {
                    val isCompleted = i < currentInCycle
                    val isCurrent = i == currentInCycle && mode == TimerMode.WORK
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isCurrent) 12.dp else 10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCompleted -> accentContentColor
                                    isCurrent -> accentContentColor.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            )
                    )
                }
                Text(
                    text = "  (${currentInCycle + 1}/$totalRounds)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Central Circular Timer
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .clickable(enabled = status == TimerStatus.IDLE) {
                        showDurationDialog = true
                    }
                    .testTag("timer_dial_box"),
                contentAlignment = Alignment.Center
            ) {
                val outlineColor = MaterialTheme.colorScheme.surfaceVariant
                val tickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                val brushGradient = Brush.linearGradient(
                    colors = listOf(
                        accentContentColor,
                        secondaryAccent,
                        primaryColor
                    )
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 16.dp.toPx()
                    val outerPadding = 10.dp.toPx()
                    val diameter = size.minDimension - strokeWidth - outerPadding * 2
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)
                    val radius = diameter / 2f

                    // Subtle 60 outer tick marks
                    val outerTickRadius = radius + strokeWidth / 2f + 6.dp.toPx()
                    for (tick in 0 until 60) {
                        val angleRad = Math.toRadians((tick * 6.0) - 90.0)
                        val isMajor = tick % 5 == 0
                        val tickLen = if (isMajor) 6.dp.toPx() else 3.dp.toPx()
                        val startR = outerTickRadius - tickLen
                        val startPt = Offset(
                            x = centerOffset.x + (startR * cos(angleRad)).toFloat(),
                            y = centerOffset.y + (startR * sin(angleRad)).toFloat()
                        )
                        val endPt = Offset(
                            x = centerOffset.x + (outerTickRadius * cos(angleRad)).toFloat(),
                            y = centerOffset.y + (outerTickRadius * sin(angleRad)).toFloat()
                        )
                        drawLine(
                            color = if (isMajor) accentContentColor.copy(alpha = 0.45f) else tickColor,
                            start = startPt,
                            end = endPt,
                            strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Background track
                    drawArc(
                        color = outlineColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active progress arc
                    val sweep = 360f * animatedProgress
                    drawArc(
                        brush = brushGradient,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Leading glowing orb on the tip of the arc
                    if (animatedProgress > 0.01f) {
                        val tipAngleRad = Math.toRadians((-90f + sweep).toDouble())
                        val tipOffset = Offset(
                            x = centerOffset.x + (radius * cos(tipAngleRad)).toFloat(),
                            y = centerOffset.y + (radius * sin(tipAngleRad)).toFloat()
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 4.5.dp.toPx(),
                            center = tipOffset
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentContentColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = when (mode) {
                                TimerMode.WORK -> "● FOCUS FLOW"
                                TimerMode.SHORT_BREAK -> "● SHORT BREAK"
                                TimerMode.LONG_BREAK -> "● LONG BREAK"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentContentColor,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val minutes = secondsRemaining / 60
                    val seconds = secondsRemaining % 60
                    val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (status) {
                                TimerStatus.RUNNING -> if (mode == TimerMode.WORK) "Stay Mindful" else "Breathe & Rest"
                                TimerStatus.PAUSED -> "Paused"
                                TimerStatus.IDLE -> "Tap time to adjust"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (status == TimerStatus.IDLE) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "Adjust Duration",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    val goalMins = settings.dailyGoalMinutes.coerceAtLeast(15)
                    Text(
                        text = "Today: ${todayFocusMinutes}m / ${goalMins}m goal",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentContentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Active Task Badge (with 1-tap complete & Quick Task Switcher)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    accentContentColor.copy(alpha = 0.35f)
                ),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showQuickTaskSheet = true }
                    .testTag("active_task_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accentContentColor.copy(alpha = 0.15f))
                                .clickable(enabled = activeTask != null) {
                                    activeTask?.let { viewModel.toggleTaskComplete(it) }
                                }
                                .testTag("complete_active_task_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Complete Task",
                                tint = accentContentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeTask?.title ?: "Select or Add a Focus Task",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            if (activeTask != null) {
                                val est = (activeTask?.estimatedPomodoros ?: 1).coerceAtLeast(1)
                                val comp = activeTask?.completedPomodoros ?: 0
                                val prio = TaskPriority.fromLevel(activeTask?.priority ?: 1)
                                Text(
                                    text = "${prio.badge} • ${activeTask?.tag} • 🍅 $comp/$est",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accentContentColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (comp.toFloat() / est.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = accentContentColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Tap to pick or quick-add a task",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accentContentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (activeTask != null) "Switch" else "Select",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentContentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Control Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                IconButton(
                    onClick = { viewModel.resetTimer() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Timer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Big Start/Pause Button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(elevation = 6.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(primaryColor)
                        .clickable {
                            if (status == TimerStatus.RUNNING) {
                                viewModel.pauseTimer()
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    try {
                                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } catch (_: Throwable) {}
                                }
                                viewModel.startTimer()
                            }
                        }
                        .testTag("start_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (status == TimerStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (status == TimerStatus.RUNNING) "Pause" else "Start",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Skip Button
                IconButton(
                    onClick = { viewModel.skipToNext() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("skip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip Session",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // +5m Quick Add Button
                IconButton(
                    onClick = { viewModel.addFiveMinutes() },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("add_five_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "+5m",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Ambient Soundscape Bar
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showSoundSheet = true }
                    .padding(2.dp)
                    .testTag("ambient_sound_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Ambient Sound",
                            tint = if (isAmbientPlaying) accentContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Soundscape: ${ambientType.displayName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isAmbientPlaying) "Playing calming background" else "Tap to choose atmosphere",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleAmbientPlayback() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isAmbientPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isAmbientPlaying) "Mute Soundscape" else "Play Soundscape",
                                tint = accentContentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isAmbientPlaying) accentContentColor.copy(alpha = 0.15f) else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isAmbientPlaying) "ACTIVE" else "SELECT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isAmbientPlaying) accentContentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Quick Duration Adjuster Dialog (when tapping timer digits while IDLE)
    if (showDurationDialog) {
        val initialMinutes = (totalSeconds / 60).coerceAtLeast(1)
        var selectedMinutes by remember(initialMinutes) { mutableIntStateOf(initialMinutes) }
        val presets = when (mode) {
            TimerMode.WORK -> listOf(15, 25, 30, 45, 60, 90)
            TimerMode.SHORT_BREAK -> listOf(3, 5, 10, 15)
            TimerMode.LONG_BREAK -> listOf(10, 15, 20, 30)
        }

        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = {
                Text(
                    text = "Adjust ${mode.title} Duration",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        IconButton(
                            onClick = { if (selectedMinutes > 1) selectedMinutes -= 1 },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text("-1", fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "$selectedMinutes min",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentContentColor
                        )

                        IconButton(
                            onClick = { if (selectedMinutes < 180) selectedMinutes += 1 },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text("+1", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Quick Presets",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            val isChosen = selectedMinutes == preset
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChosen) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedMinutes = preset }
                            ) {
                                Text(
                                    text = "${preset}m",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isChosen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setCustomTimerMinutes(selectedMinutes)
                        showDurationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                ) {
                    Text("Apply", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDurationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Quick Task Switcher & Quick Add Bottom Sheet
    if (showQuickTaskSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var quickTaskTitle by remember { mutableStateOf("") }
        val incompleteTasks = allTasks.filter { !it.isCompleted }

        ModalBottomSheet(
            onDismissRequest = { showQuickTaskSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Active Focus Task",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Switch your focus target or quick-add a new task",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            showQuickTaskSheet = false
                            onNavigateToTasks()
                        }
                    ) {
                        Text("All Tasks", color = accentContentColor, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "All Tasks",
                            tint = accentContentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick-add input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quickTaskTitle,
                        onValueChange = { quickTaskTitle = it },
                        placeholder = { Text("Quick add a task...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (quickTaskTitle.isNotBlank()) {
                                viewModel.addNewTask(
                                    title = quickTaskTitle,
                                    tag = "Work",
                                    notes = "",
                                    estimatedPomodoros = 2,
                                    priority = 1
                                )
                                quickTaskTitle = ""
                            }
                        },
                        enabled = quickTaskTitle.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (incompleteTasks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No pending tasks! Add one above or open Tasks tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(incompleteTasks, key = { it.id }) { task ->
                            val isSelected = activeTask?.id == task.id
                            val prio = TaskPriority.fromLevel(task.priority)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) accentContentColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, accentContentColor) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        viewModel.setActiveTask(task)
                                        showQuickTaskSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${prio.badge} • ${task.tag} • 🍅 ${task.completedPomodoros}/${task.estimatedPomodoros}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accentContentColor
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = accentContentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Modal Bottom Sheet for Ambient Sound Selection & Volume
    if (showSoundSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSoundSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ambient Soundscapes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showSoundSheet = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Hi-Fi procedural soundscapes to boost focus and block distractions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Sound choices
                AmbientSoundType.values().forEach { sound ->
                    val isSelected = ambientType == sound
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) accentContentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, accentContentColor) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.setAmbientSound(sound)
                                if (sound != AmbientSoundType.NONE && !isAmbientPlaying) {
                                    viewModel.toggleAmbientPlayback()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = sound.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) accentContentColor else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = sound.iconDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = accentContentColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Volume Slider
                Text(
                    text = "Volume (${(ambientVolume * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                        contentDescription = "Volume Down",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = ambientVolume,
                        onValueChange = { viewModel.setAmbientVolume(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor
                        )
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume Up",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
