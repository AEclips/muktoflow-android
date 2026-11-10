package com.example.data.model

enum class AmbientSoundType(val displayName: String, val iconDescription: String) {
    NONE("None", "Sound off"),
    RAIN("Rainfall", "Calming gentle rain & soft droplets"),
    STREAM("Forest Stream", "Babbling mountain brook"),
    OCEAN("Ocean Waves", "Rhythmic coastal surf & tide"),
    CAFE("Zen Cafe", "Warm coffee shop acoustic hum"),
    BINAURAL("Deep Focus 40Hz", "Binaural harmonic concentration drone"),
    WHITE_NOISE("Soft Pink Noise", "Smooth distraction-blocking static")
}

enum class TimerMode(val title: String, val defaultMinutes: Int) {
    WORK("Focus", 25),
    SHORT_BREAK("Short Break", 5),
    LONG_BREAK("Long Break", 15)
}

enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED
}

enum class ThemePreference(val displayName: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class TaskPriority(val level: Int, val label: String, val badge: String) {
    LOW(0, "Low", "🌱 Low"),
    MEDIUM(1, "Medium", "⚡ Med"),
    HIGH(2, "High", "🔥 High");

    companion object {
        fun fromLevel(level: Int): TaskPriority = values().find { it.level == level } ?: MEDIUM
    }
}

data class PomodoroSettings(
    val workDurationMinutes: Int = 25,
    val shortBreakDurationMinutes: Int = 5,
    val longBreakDurationMinutes: Int = 15,
    val roundsBeforeLongBreak: Int = 4,
    val autoStartBreaks: Boolean = false,
    val autoStartWork: Boolean = false,
    val soundAlertEnabled: Boolean = true,
    val vibrationAlertEnabled: Boolean = true,
    val ambientSoundType: AmbientSoundType = AmbientSoundType.NONE,
    val ambientVolume: Float = 0.5f,
    val tickingSound: Boolean = false,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val dailyGoalMinutes: Int = 120,
    val keepScreenAwake: Boolean = true
)
