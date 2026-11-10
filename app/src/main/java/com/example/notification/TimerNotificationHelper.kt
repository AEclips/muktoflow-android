package com.example.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.model.TimerMode
import com.example.data.model.TimerStatus
import java.util.Locale

object TimerNotificationHelper {
    const val CHANNEL_ID_LIVE = "muktoflow_live_timer_channel"
    const val CHANNEL_ID_COMPLETE = "muktoflow_timer_complete_channel"
    const val NOTIFICATION_ID_LIVE = 1001
    const val NOTIFICATION_ID_COMPLETE = 1002

    const val ACTION_TOGGLE_PAUSE = "com.example.notification.ACTION_TOGGLE_PAUSE"
    const val ACTION_ADD_FIVE = "com.example.notification.ACTION_ADD_FIVE"
    const val ACTION_RESET = "com.example.notification.ACTION_RESET"
    const val ACTION_SKIP = "com.example.notification.ACTION_SKIP"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val liveChannel = NotificationChannel(
                CHANNEL_ID_LIVE,
                "Active Timer (Live Countdown)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the live Pomodoro & Break countdown timer like Google Clock"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val completeChannel = NotificationChannel(
                CHANNEL_ID_COMPLETE,
                "Timer Completion Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a Focus or Break session finishes"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(liveChannel)
            manager.createNotificationChannel(completeChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildLiveTimerNotification(
        context: Context,
        mode: TimerMode,
        status: TimerStatus,
        secondsRemaining: Int,
        totalSeconds: Int,
        activeTaskTitle: String?,
        roundInfo: String
    ): Notification {
        ensureChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val togglePauseIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TimerActionReceiver::class.java).apply { action = ACTION_TOGGLE_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val addFiveIntent = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, TimerActionReceiver::class.java).apply { action = ACTION_ADD_FIVE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resetIntent = PendingIntent.getBroadcast(
            context,
            3,
            Intent(context, TimerActionReceiver::class.java).apply { action = ACTION_RESET },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mins = secondsRemaining / 60
        val secs = secondsRemaining % 60
        val formattedTime = String.format(Locale.US, "%02d:%02d", mins, secs)

        val modeLabel = when (mode) {
            TimerMode.WORK -> "🍅 Focus Session"
            TimerMode.SHORT_BREAK -> "☕ Short Break"
            TimerMode.LONG_BREAK -> "🌿 Long Break"
        }

        val accentColor = when (mode) {
            TimerMode.WORK -> 0xFF2E6B4E.toInt()
            TimerMode.SHORT_BREAK -> 0xFF2D6A4F.toInt()
            TimerMode.LONG_BREAK -> 0xFFB45309.toInt()
        }

        val isRunning = status == TimerStatus.RUNNING
        val titleText = if (isRunning) {
            "$modeLabel • $roundInfo"
        } else {
            "⏸ Paused ($formattedTime) • ${mode.title}"
        }

        val subtitleText = if (!activeTaskTitle.isNullOrBlank()) {
            "Task: $activeTaskTitle"
        } else {
            if (mode == TimerMode.WORK) "Stay mindful & focused" else "Breathe & recharge"
        }

        val elapsed = (totalSeconds - secondsRemaining).coerceIn(0, totalSeconds.coerceAtLeast(1))

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_LIVE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(titleText)
            .setContentText(subtitleText)
            .setSubText("MuktoFlow")
            .setContentIntent(contentPendingIntent)
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setColor(accentColor)
            .setColorized(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(totalSeconds.coerceAtLeast(1), elapsed, false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        // Google Clock style native live countdown chronometer in notification header
        if (isRunning) {
            builder
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + (secondsRemaining * 1000L))
                .setShowWhen(true)
        } else {
            builder
                .setUsesChronometer(false)
                .setShowWhen(false)
        }

        val pauseResumeLabel = if (isRunning) "Pause" else "Resume"
        val pauseResumeIcon = if (isRunning) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        builder.addAction(pauseResumeIcon, pauseResumeLabel, togglePauseIntent)
        builder.addAction(android.R.drawable.ic_input_add, "+5 Min", addFiveIntent)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reset", resetIntent)

        return builder.build()
    }

    fun updateOrShowLiveNotification(
        context: Context,
        mode: TimerMode,
        status: TimerStatus,
        secondsRemaining: Int,
        totalSeconds: Int,
        activeTaskTitle: String?,
        roundInfo: String
    ) {
        if (!hasNotificationPermission(context)) return
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val notification = buildLiveTimerNotification(
                context = context,
                mode = mode,
                status = status,
                secondsRemaining = secondsRemaining,
                totalSeconds = totalSeconds,
                activeTaskTitle = activeTaskTitle,
                roundInfo = roundInfo
            )
            manager.notify(NOTIFICATION_ID_LIVE, notification)
        } catch (_: Throwable) {}
    }

    fun cancelLiveNotification(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID_LIVE)
        } catch (_: Throwable) {}
    }

    fun showSessionCompletedNotification(
        context: Context,
        completedMode: TimerMode,
        nextMode: TimerMode,
        activeTaskTitle: String?
    ) {
        if (!hasNotificationPermission(context)) return
        try {
            ensureChannels(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                10,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val startNextIntent = PendingIntent.getBroadcast(
                context,
                11,
                Intent(context, TimerActionReceiver::class.java).apply { action = ACTION_TOGGLE_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (completedMode == TimerMode.WORK) {
                "Focus Session Complete! 🎉"
            } else {
                "Break Finished! ⚡"
            }

            val body = if (completedMode == TimerMode.WORK) {
                val taskPart = if (!activeTaskTitle.isNullOrBlank()) "Great job on '$activeTaskTitle'! " else ""
                "${taskPart}Ready for your ${nextMode.title}?"
            } else {
                "Time to get back into mindful focus!"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_COMPLETE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(contentPendingIntent)
                .setAutoCancel(true)
                .setColor(0xFF2E6B4E.toInt())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                    android.R.drawable.ic_media_play,
                    "Start ${nextMode.title}",
                    startNextIntent
                )
                .build()

            manager.notify(NOTIFICATION_ID_COMPLETE, notification)
        } catch (_: Throwable) {}
    }
}
