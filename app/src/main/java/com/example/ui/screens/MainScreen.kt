package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.FocusColorLight
import com.example.ui.theme.focusAccentColor
import com.example.viewmodel.PomodoroViewModel

enum class NavigationItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    TIMER("Timer", Icons.Filled.Timer, Icons.Outlined.Timer, "nav_timer"),
    TASKS("Tasks", Icons.Filled.Checklist, Icons.Outlined.Checklist, "nav_tasks"),
    ANALYTICS("Analytics", Icons.Filled.BarChart, Icons.Outlined.BarChart, "nav_analytics"),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
}

@Composable
fun MainScreen(viewModel: PomodoroViewModel) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val isZenFullscreen by viewModel.isZenFullscreen.collectAsStateWithLifecycle()
    val pendingTasksCount = allTasks.count { !it.isCompleted }
    var showImportDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Intercept back button if in Zen Fullscreen or on non-timer screen
    BackHandler(enabled = isZenFullscreen || selectedIndex != 0) {
        if (isZenFullscreen) {
            viewModel.toggleZenFullscreen()
        } else {
            selectedIndex = 0
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = !isZenFullscreen,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .testTag("main_bottom_nav"),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    val selectedNavColor = focusAccentColor()
                    NavigationItem.values().forEachIndexed { index, item ->
                        val isSelected = selectedIndex == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedIndex = index },
                            icon = {
                                if (item == NavigationItem.TASKS && pendingTasksCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = FocusColorLight,
                                                contentColor = Color.White
                                            ) {
                                                Text(pendingTasksCount.toString())
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.title
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = selectedNavColor,
                                selectedTextColor = selectedNavColor,
                                indicatorColor = selectedNavColor.copy(alpha = 0.18f)
                            ),
                            modifier = Modifier.testTag(item.tag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)

        Crossfade(
            targetState = selectedIndex,
            animationSpec = tween(durationMillis = 220),
            label = "screenCrossfade"
        ) { tabIndex ->
            when (tabIndex) {
                0 -> TimerScreen(
                    viewModel = viewModel,
                    onNavigateToTasks = { selectedIndex = 1 },
                    modifier = contentModifier
                )
                1 -> TasksScreen(
                    viewModel = viewModel,
                    onTaskSelectedForFocus = { selectedIndex = 0 },
                    modifier = contentModifier
                )
                2 -> AnalyticsScreen(
                    viewModel = viewModel,
                    modifier = contentModifier
                )
                3 -> SettingsScreen(
                    viewModel = viewModel,
                    modifier = contentModifier
                )
            }
        }
    }

    if (showImportDialog) {
        com.example.ui.components.ImportTasksDialog(viewModel = viewModel, onDismiss = { showImportDialog = false })
    }
}
