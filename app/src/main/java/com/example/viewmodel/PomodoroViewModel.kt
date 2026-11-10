package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioEngine
import com.example.data.db.ZenFocusDatabase
import com.example.data.model.AmbientSoundType
import com.example.data.model.PomodoroSettings
import com.example.data.model.SessionLogEntity
import com.example.data.model.TaskEntity
import com.example.data.model.ThemePreference
import com.example.data.model.TimerMode
import com.example.data.model.TimerStatus
import com.example.data.repository.PomodoroRepository
import com.example.data.pomofocus.PomofocusSyncEngine
import com.example.data.pomofocus.PomofocusSyncState
import com.example.data.pomofocus.PomofocusUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PomodoroViewModel(application: Application) : AndroidViewModel(application) {
    private val database = ZenFocusDatabase.getDatabase(application)
    private val repository = PomodoroRepository(database.taskDao(), database.sessionLogDao())
    val audioEngine = AudioEngine()
    val pomofocusSyncEngine = PomofocusSyncEngine(application)
    val pomofocusSyncState: StateFlow<PomofocusSyncState> = pomofocusSyncEngine.syncState

    private val prefs: SharedPreferences =
        application.getSharedPreferences("zenfocus_prefs", Context.MODE_PRIVATE)

    // Settings
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<PomodoroSettings> = _settings.asStateFlow()

    // Timer State
    private val _timerMode = MutableStateFlow(TimerMode.WORK)
    val timerMode: StateFlow<TimerMode> = _timerMode.asStateFlow()

    private val _timerStatus = MutableStateFlow(TimerStatus.IDLE)
    val timerStatus: StateFlow<TimerStatus> = _timerStatus.asStateFlow()

    private val _secondsRemaining = MutableStateFlow(25 * 60)
    val secondsRemaining: StateFlow<Int> = _secondsRemaining.asStateFlow()

    private val _totalSecondsForMode = MutableStateFlow(25 * 60)
    val totalSecondsForMode: StateFlow<Int> = _totalSecondsForMode.asStateFlow()

    private val _completedRoundsInCycle = MutableStateFlow(0)
    val completedRoundsInCycle: StateFlow<Int> = _completedRoundsInCycle.asStateFlow()

    private val _activeTask = MutableStateFlow<TaskEntity?>(null)
    val activeTask: StateFlow<TaskEntity?> = _activeTask.asStateFlow()

    // Zen Fullscreen / Focus Lock Mode
    private val _isZenFullscreen = MutableStateFlow(false)
    val isZenFullscreen: StateFlow<Boolean> = _isZenFullscreen.asStateFlow()

    // Ambient Sound
    private val _currentAmbientType = MutableStateFlow(_settings.value.ambientSoundType)
    val currentAmbientType: StateFlow<AmbientSoundType> = _currentAmbientType.asStateFlow()

    private val _ambientVolume = MutableStateFlow(_settings.value.ambientVolume)
    val ambientVolume: StateFlow<Float> = _ambientVolume.asStateFlow()

    private val _isAmbientPlaying = MutableStateFlow(false)
    val isAmbientPlaying: StateFlow<Boolean> = _isAmbientPlaying.asStateFlow()

    // Tasks & Analytics from DB
    val allTasks: StateFlow<List<TaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLogs: StateFlow<List<SessionLogEntity>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayFocusMinutes: StateFlow<Int> = repository.getTodayFocusSeconds()
        .map { (it ?: 0) / 60 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val streakDays: StateFlow<Int> = repository.distinctWorkDates
        .map { PomodoroRepository.calculateStreak(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var timerJob: Job? = null
    private var targetEndTimeElapsedMillis: Long = 0L

    init {
        resetTimerToMode(TimerMode.WORK)
        com.example.notification.TimerNotificationHelper.ensureChannels(application)

        if (pomofocusSyncState.value.isConnected && pomofocusSyncState.value.autoSyncEnabled) {
            syncPomofocusNow()
        }

        // Keep activeTask synchronized with DB updates & restore saved active task
        viewModelScope.launch {
            repository.allTasks.collect { tasks ->
                val currentActive = _activeTask.value
                val savedId = prefs.getLong("active_task_id", -1L)
                if (currentActive != null) {
                    val updatedInDb = tasks.find { it.id == currentActive.id }
                    if (updatedInDb == null || updatedInDb.isCompleted) {
                        val nextIncomplete = tasks.firstOrNull { !it.isCompleted }
                        _activeTask.value = nextIncomplete
                        prefs.edit().putLong("active_task_id", nextIncomplete?.id ?: -1L).apply()
                    } else if (updatedInDb != currentActive) {
                        _activeTask.value = updatedInDb
                    }
                } else {
                    val restored = if (savedId > 0L) {
                        tasks.find { it.id == savedId && !it.isCompleted }
                    } else null
                    val picked = restored ?: tasks.firstOrNull { !it.isCompleted }
                    if (picked != null) {
                        _activeTask.value = picked
                        prefs.edit().putLong("active_task_id", picked.id).apply()
                    }
                }
            }
        }

        viewModelScope.launch {
            com.example.notification.TimerActionBus.actions.collect { action ->
                when (action) {
                    com.example.notification.TimerNotificationHelper.ACTION_TOGGLE_PAUSE -> {
                        if (_timerStatus.value == TimerStatus.RUNNING) {
                            pauseTimer()
                        } else {
                            startTimer()
                        }
                    }
                    com.example.notification.TimerNotificationHelper.ACTION_ADD_FIVE -> {
                        addFiveMinutes()
                    }
                    com.example.notification.TimerNotificationHelper.ACTION_RESET -> {
                        resetTimer()
                    }
                    com.example.notification.TimerNotificationHelper.ACTION_SKIP -> {
                        skipToNext()
                    }
                }
            }
        }
    }

    fun toggleZenFullscreen() {
        _isZenFullscreen.value = !_isZenFullscreen.value
    }

    private fun currentRoundInfo(): String {
        val total = _settings.value.roundsBeforeLongBreak.coerceAtLeast(1)
        val curr = (_completedRoundsInCycle.value % total) + 1
        return "Round $curr/$total"
    }

    fun refreshLiveNotification() {
        if (_timerStatus.value != TimerStatus.IDLE) {
            syncLiveNotification(startService = _timerStatus.value == TimerStatus.RUNNING)
        }
    }

    private fun syncLiveNotification(startService: Boolean = false) {
        val app = getApplication<Application>()
        val status = _timerStatus.value
        if (status == TimerStatus.IDLE) {
            com.example.notification.TimerForegroundService.stop(app)
            return
        }
        if (startService) {
            com.example.notification.TimerForegroundService.startOrUpdate(
                context = app,
                mode = _timerMode.value,
                status = status,
                secondsRemaining = _secondsRemaining.value,
                totalSeconds = _totalSecondsForMode.value,
                activeTaskTitle = _activeTask.value?.title,
                roundInfo = currentRoundInfo()
            )
        } else {
            com.example.notification.TimerNotificationHelper.updateOrShowLiveNotification(
                context = app,
                mode = _timerMode.value,
                status = status,
                secondsRemaining = _secondsRemaining.value,
                totalSeconds = _totalSecondsForMode.value,
                activeTaskTitle = _activeTask.value?.title,
                roundInfo = currentRoundInfo()
            )
        }
    }

    private fun loadSettings(): PomodoroSettings {
        val soundTypeStr = prefs.getString("ambient_type", AmbientSoundType.NONE.name)
        val ambientType = try {
            AmbientSoundType.valueOf(soundTypeStr ?: AmbientSoundType.NONE.name)
        } catch (_: Exception) {
            AmbientSoundType.NONE
        }
        val themeStr = prefs.getString("theme_preference", ThemePreference.SYSTEM.name)
        val themePref = try {
            ThemePreference.valueOf(themeStr ?: ThemePreference.SYSTEM.name)
        } catch (_: Exception) {
            ThemePreference.SYSTEM
        }

        return PomodoroSettings(
            workDurationMinutes = prefs.getInt("work_minutes", 25),
            shortBreakDurationMinutes = prefs.getInt("short_break_minutes", 5),
            longBreakDurationMinutes = prefs.getInt("long_break_minutes", 15),
            roundsBeforeLongBreak = prefs.getInt("rounds_before_long", 4),
            autoStartBreaks = prefs.getBoolean("auto_start_breaks", false),
            autoStartWork = prefs.getBoolean("auto_start_work", false),
            soundAlertEnabled = prefs.getBoolean("sound_alert", true),
            vibrationAlertEnabled = prefs.getBoolean("vibration_alert", true),
            ambientSoundType = ambientType,
            ambientVolume = prefs.getFloat("ambient_volume", 0.5f),
            tickingSound = prefs.getBoolean("ticking_sound", false),
            themePreference = themePref,
            dailyGoalMinutes = prefs.getInt("daily_goal_minutes", 120),
            keepScreenAwake = prefs.getBoolean("keep_screen_awake", true)
        )
    }

    private fun saveSettings(s: PomodoroSettings) {
        prefs.edit()
            .putInt("work_minutes", s.workDurationMinutes)
            .putInt("short_break_minutes", s.shortBreakDurationMinutes)
            .putInt("long_break_minutes", s.longBreakDurationMinutes)
            .putInt("rounds_before_long", s.roundsBeforeLongBreak)
            .putBoolean("auto_start_breaks", s.autoStartBreaks)
            .putBoolean("auto_start_work", s.autoStartWork)
            .putBoolean("sound_alert", s.soundAlertEnabled)
            .putBoolean("vibration_alert", s.vibrationAlertEnabled)
            .putString("ambient_type", s.ambientSoundType.name)
            .putFloat("ambient_volume", s.ambientVolume)
            .putBoolean("ticking_sound", s.tickingSound)
            .putString("theme_preference", s.themePreference.name)
            .putInt("daily_goal_minutes", s.dailyGoalMinutes)
            .putBoolean("keep_screen_awake", s.keepScreenAwake)
            .apply()
    }

    fun resetSettingsToDefaults() {
        val currentTheme = _settings.value.themePreference
        val defaults = PomodoroSettings(themePreference = currentTheme)
        updateSettings(defaults)
        setAmbientSound(AmbientSoundType.NONE)
    }

    fun cycleThemePreference() {
        val next = when (_settings.value.themePreference) {
            ThemePreference.SYSTEM -> ThemePreference.DARK
            ThemePreference.DARK -> ThemePreference.LIGHT
            ThemePreference.LIGHT -> ThemePreference.SYSTEM
        }
        updateSettings(_settings.value.copy(themePreference = next))
    }

    fun updateSettings(newSettings: PomodoroSettings) {
        _settings.value = newSettings
        saveSettings(newSettings)
        if (_timerStatus.value == TimerStatus.IDLE) {
            resetTimerToMode(_timerMode.value)
        }
    }

    fun setCustomTimerMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 180)
        when (_timerMode.value) {
            TimerMode.WORK -> updateSettings(_settings.value.copy(workDurationMinutes = clamped))
            TimerMode.SHORT_BREAK -> updateSettings(_settings.value.copy(shortBreakDurationMinutes = clamped))
            TimerMode.LONG_BREAK -> updateSettings(_settings.value.copy(longBreakDurationMinutes = clamped))
        }
        resetTimerToMode(_timerMode.value)
    }

    fun selectMode(mode: TimerMode) {
        pauseTimer()
        _timerMode.value = mode
        resetTimerToMode(mode)
        com.example.notification.TimerForegroundService.stop(getApplication())
    }

    private fun getDurationForMode(mode: TimerMode): Int {
        val mins = when (mode) {
            TimerMode.WORK -> _settings.value.workDurationMinutes
            TimerMode.SHORT_BREAK -> _settings.value.shortBreakDurationMinutes
            TimerMode.LONG_BREAK -> _settings.value.longBreakDurationMinutes
        }
        return mins * 60
    }

    private fun resetTimerToMode(mode: TimerMode) {
        val totalSec = getDurationForMode(mode)
        _totalSecondsForMode.value = totalSec
        _secondsRemaining.value = totalSec
        _timerStatus.value = TimerStatus.IDLE
    }

    fun startTimer() {
        if (_timerStatus.value == TimerStatus.RUNNING) return
        _timerStatus.value = TimerStatus.RUNNING
        targetEndTimeElapsedMillis = SystemClock.elapsedRealtime() + (_secondsRemaining.value * 1000L)
        syncLiveNotification(startService = true)

        // Start ambient sound if selected
        if (_timerMode.value == TimerMode.WORK && _currentAmbientType.value != AmbientSoundType.NONE) {
            audioEngine.startAmbient(_currentAmbientType.value, _ambientVolume.value)
            _isAmbientPlaying.value = true
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive && _secondsRemaining.value > 0) {
                delay(1000)
                if (_secondsRemaining.value > 0) {
                    val nowElapsed = SystemClock.elapsedRealtime()
                    val wallRemaining = (((targetEndTimeElapsedMillis - nowElapsed) + 999L) / 1000L).toInt()
                    val nextSec = _secondsRemaining.value - 1
                    _secondsRemaining.value = minOf(nextSec, wallRemaining).coerceAtLeast(0)

                    // Update notification progress bar every 15s (SystemUI Chronometer handles 1Hz countdown natively)
                    if (_secondsRemaining.value % 15 == 0) {
                        syncLiveNotification(startService = false)
                    }
                    if (_settings.value.tickingSound && _timerMode.value == TimerMode.WORK) {
                        audioEngine.playTick(0.18f)
                    }
                }
            }
            if (_secondsRemaining.value <= 0) {
                onTimerFinished()
            }
        }
    }

    fun pauseTimer() {
        val wasRunningOrPaused = _timerStatus.value != TimerStatus.IDLE
        timerJob?.cancel()
        timerJob = null
        _timerStatus.value = TimerStatus.PAUSED
        if (_isAmbientPlaying.value) {
            audioEngine.stopAmbient()
            _isAmbientPlaying.value = false
        }
        if (wasRunningOrPaused) {
            syncLiveNotification(startService = false)
        }
    }

    fun resetTimer() {
        pauseTimer()
        resetTimerToMode(_timerMode.value)
        com.example.notification.TimerForegroundService.stop(getApplication())
    }

    fun addFiveMinutes() {
        val additional = 5 * 60
        _secondsRemaining.value += additional
        _totalSecondsForMode.value += additional
        if (_timerStatus.value == TimerStatus.RUNNING) {
            targetEndTimeElapsedMillis += (additional * 1000L)
        }
        if (_timerStatus.value != TimerStatus.IDLE) {
            syncLiveNotification(startService = false)
        }
    }

    fun skipToNext() {
        pauseTimer()
        transitionToNextMode(autoStart = false)
        com.example.notification.TimerForegroundService.stop(getApplication())
    }

    private fun onTimerFinished() {
        _timerStatus.value = TimerStatus.IDLE
        audioEngine.stopAmbient()
        _isAmbientPlaying.value = false
        com.example.notification.TimerForegroundService.stop(getApplication())

        // Trigger notifications
        if (_settings.value.soundAlertEnabled) {
            audioEngine.playChime()
        }
        if (_settings.value.vibrationAlertEnabled) {
            triggerVibration()
        }

        val completedMode = _timerMode.value
        val durationSec = _totalSecondsForMode.value
        val task = _activeTask.value

        // Log to database
        viewModelScope.launch {
            repository.logSession(
                taskId = task?.id,
                taskTitle = task?.title,
                tag = task?.tag ?: "General",
                sessionType = completedMode.name,
                durationSeconds = durationSec
            )
            if (completedMode == TimerMode.WORK && task != null) {
                _activeTask.value = task.copy(completedPomodoros = task.completedPomodoros + 1)
            }
        }

        if (completedMode == TimerMode.WORK) {
            val newRounds = _completedRoundsInCycle.value + 1
            _completedRoundsInCycle.value = newRounds
            val shouldLongBreak = newRounds >= _settings.value.roundsBeforeLongBreak

            if (pomofocusSyncState.value.isConnected && pomofocusSyncState.value.autoSyncEnabled) {
                syncPomofocusNow()
            }

            val nextMode = if (shouldLongBreak) TimerMode.LONG_BREAK else TimerMode.SHORT_BREAK
            _timerMode.value = nextMode
            resetTimerToMode(nextMode)

            com.example.notification.TimerNotificationHelper.showSessionCompletedNotification(
                context = getApplication(),
                completedMode = completedMode,
                nextMode = nextMode,
                activeTaskTitle = task?.title
            )

            if (_settings.value.autoStartBreaks) {
                startTimer()
            }
        } else {
            // Break finished
            if (completedMode == TimerMode.LONG_BREAK) {
                _completedRoundsInCycle.value = 0
            }
            _timerMode.value = TimerMode.WORK
            resetTimerToMode(TimerMode.WORK)

            com.example.notification.TimerNotificationHelper.showSessionCompletedNotification(
                context = getApplication(),
                completedMode = completedMode,
                nextMode = TimerMode.WORK,
                activeTaskTitle = task?.title
            )

            if (_settings.value.autoStartWork) {
                startTimer()
            }
        }
    }

    private fun transitionToNextMode(autoStart: Boolean) {
        val current = _timerMode.value
        if (current == TimerMode.WORK) {
            val newRounds = _completedRoundsInCycle.value + 1
            _completedRoundsInCycle.value = newRounds
            val next = if (newRounds >= _settings.value.roundsBeforeLongBreak) TimerMode.LONG_BREAK else TimerMode.SHORT_BREAK
            _timerMode.value = next
            resetTimerToMode(next)
        } else {
            if (current == TimerMode.LONG_BREAK) {
                _completedRoundsInCycle.value = 0
            }
            _timerMode.value = TimerMode.WORK
            resetTimerToMode(TimerMode.WORK)
        }
        if (autoStart) {
            startTimer()
        }
    }

    fun setAmbientSound(type: AmbientSoundType) {
        _currentAmbientType.value = type
        _settings.value = _settings.value.copy(ambientSoundType = type)
        saveSettings(_settings.value)
        if (type == AmbientSoundType.NONE) {
            audioEngine.stopAmbient()
            _isAmbientPlaying.value = false
        } else if (_isAmbientPlaying.value || (_timerStatus.value == TimerStatus.RUNNING && _timerMode.value == TimerMode.WORK)) {
            audioEngine.startAmbient(type, _ambientVolume.value)
            _isAmbientPlaying.value = true
        }
    }

    fun setAmbientVolume(vol: Float) {
        _ambientVolume.value = vol
        _settings.value = _settings.value.copy(ambientVolume = vol)
        saveSettings(_settings.value)
        audioEngine.setVolume(vol)
    }

    fun toggleAmbientPlayback() {
        if (_isAmbientPlaying.value) {
            audioEngine.stopAmbient()
            _isAmbientPlaying.value = false
        } else {
            val type = if (_currentAmbientType.value == AmbientSoundType.NONE) AmbientSoundType.RAIN else _currentAmbientType.value
            _currentAmbientType.value = type
            _settings.value = _settings.value.copy(ambientSoundType = type)
            saveSettings(_settings.value)
            audioEngine.startAmbient(type, _ambientVolume.value)
            _isAmbientPlaying.value = true
        }
    }

    fun setActiveTask(task: TaskEntity?) {
        _activeTask.value = task
        prefs.edit().putLong("active_task_id", task?.id ?: -1L).apply()
        if (_timerStatus.value != TimerStatus.IDLE) {
            syncLiveNotification(startService = false)
        }
    }

    fun addNewTask(
        title: String,
        tag: String,
        notes: String,
        estimatedPomodoros: Int,
        priority: Int = 1
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val task = TaskEntity(
                title = title.trim(),
                tag = tag.ifBlank { "General" },
                notes = notes.trim(),
                estimatedPomodoros = estimatedPomodoros.coerceAtLeast(1),
                priority = priority.coerceIn(0, 2)
            )
            val newId = repository.insertTask(task)
            if (_activeTask.value == null) {
                val created = task.copy(id = newId)
                _activeTask.value = created
                prefs.edit().putLong("active_task_id", newId).apply()
            }
        }
    }

    fun updateExistingTask(task: TaskEntity) {
        if (task.title.isBlank()) return
        viewModelScope.launch {
            repository.updateTask(task)
            if (_activeTask.value?.id == task.id) {
                _activeTask.value = task
                if (_timerStatus.value != TimerStatus.IDLE) {
                    syncLiveNotification(startService = false)
                }
            }
        }
    }

    fun toggleTaskComplete(task: TaskEntity) {
        viewModelScope.launch {
            val updatedStatus = !task.isCompleted
            repository.setTaskCompletion(task.id, updatedStatus)
            if (updatedStatus && _settings.value.soundAlertEnabled) {
                audioEngine.playTaskCompleteSound()
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
            if (_activeTask.value?.id == task.id) {
                _activeTask.value = null
            }
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            repository.deleteCompletedTasks()
        }
    }

    fun logManualFocusSession(minutes: Int, tag: String, title: String) {
        val validMinutes = minutes.coerceIn(1, 300)
        viewModelScope.launch {
            repository.logSession(
                taskId = _activeTask.value?.id,
                taskTitle = title.ifBlank { _activeTask.value?.title ?: "Manual Focus Session" },
                tag = tag.ifBlank { "General" },
                sessionType = TimerMode.WORK.name,
                durationSeconds = validMinutes * 60
            )
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    /**
     * Imports tasks from plain text (one per line). Compatible with text copied from Pomofocus.io.
     * Supported formats per line:  "Task title", "Task title 4", "Task title #3", "[ ] Task", "- Task", "3/5 Task".
     * Returns the number of newly added tasks (duplicates by title are skipped).
     */
    fun importTasksFromText(text: String): Int {
        val existing = allTasks.value.map { it.title.trim().lowercase() }.toMutableSet()
        val parsed = mutableListOf<TaskEntity>()
        for (raw in text.lines()) {
            var line = raw.trim()
            if (line.isBlank()) continue
            line = line.replace(Regex("^(?:[-*•]\\s*)?(?:\\[[ xX]?\\]\\s*)?"), "").trim()
            var est = 1
            var done = 0
            Regex("^(\\d+)\\s*/\\s*(\\d+)\\s+(.*)$").find(line)?.let {
                done = it.groupValues[1].toInt()
                est = it.groupValues[2].toInt()
                line = it.groupValues[3].trim()
            }
            Regex("^(.*?)\\s+#?(\\d{1,2})$").find(line)?.let {
                if (it.groupValues[1].isNotBlank()) {
                    est = it.groupValues[2].toInt()
                    line = it.groupValues[1].trim()
                }
            }
            if (line.isBlank() || !existing.add(line.lowercase())) continue
            parsed.add(
                TaskEntity(
                    title = line,
                    tag = "Pomofocus",
                    estimatedPomodoros = est.coerceIn(1, 30),
                    completedPomodoros = done.coerceIn(0, 30)
                )
            )
        }
        viewModelScope.launch { parsed.forEach { repository.insertTask(it) } }
        return parsed.size
    }

    /** Exports pending tasks as text that can be pasted into Pomofocus.io (one task per line). */
    fun exportTasksAsText(): String =
        allTasks.value.filter { !it.isCompleted }
            .joinToString("\n") { "${it.title} ${it.estimatedPomodoros}" }
    /** Runs a bidirectional sync with the connected Pomofocus account. */
    fun syncPomofocusNow(onComplete: ((Boolean, String) -> Unit)? = null) {
        val currentTasks = allTasks.value
        pomofocusSyncEngine.performFullSync(
            localTasks = currentTasks,
            insertLocal = { repository.insertTask(it) },
            updateLocal = { repository.updateTask(it) },
            onComplete = { success, msg ->
                onComplete?.invoke(success, msg)
            }
        )
    }

    /** Disconnects the Pomofocus account and clears active session. */
    fun disconnectPomofocus() {
        pomofocusSyncEngine.disconnect()
    }

    /** Called when user logs in via PomofocusAuthSheet. */
    fun onPomofocusLoginSuccess(user: PomofocusUser) {
        pomofocusSyncEngine.saveAuthenticatedUser(user)
        syncPomofocusNow()
    }

    /** Toggles auto sync on session finish. */
    fun setPomofocusAutoSync(enabled: Boolean) {
        pomofocusSyncEngine.setAutoSyncEnabled(enabled)
    }

    private fun triggerVibration() {
        val context = getApplication<Application>()
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 250, 150, 350),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 250, 150, 350), -1)
            }
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        audioEngine.release()
    }
}
