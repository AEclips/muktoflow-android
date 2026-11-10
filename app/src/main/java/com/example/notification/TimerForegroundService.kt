package com.example.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.example.data.model.TimerMode
import com.example.data.model.TimerStatus

class TimerForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val modeName = intent?.getStringExtra(EXTRA_MODE) ?: TimerMode.WORK.name
        val statusName = intent?.getStringExtra(EXTRA_STATUS) ?: TimerStatus.RUNNING.name
        val secondsRemaining = intent?.getIntExtra(EXTRA_SECONDS_REMAINING, 25 * 60) ?: (25 * 60)
        val totalSeconds = intent?.getIntExtra(EXTRA_TOTAL_SECONDS, 25 * 60) ?: (25 * 60)
        val taskTitle = intent?.getStringExtra(EXTRA_TASK_TITLE)
        val roundInfo = intent?.getStringExtra(EXTRA_ROUND_INFO) ?: "Round 1/4"

        val mode = try {
            TimerMode.valueOf(modeName)
        } catch (_: Exception) {
            TimerMode.WORK
        }
        val status = try {
            TimerStatus.valueOf(statusName)
        } catch (_: Exception) {
            TimerStatus.RUNNING
        }

        val notification = TimerNotificationHelper.buildLiveTimerNotification(
            context = this,
            mode = mode,
            status = status,
            secondsRemaining = secondsRemaining,
            totalSeconds = totalSeconds,
            activeTaskTitle = taskTitle,
            roundInfo = roundInfo
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    TimerNotificationHelper.NOTIFICATION_ID_LIVE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(TimerNotificationHelper.NOTIFICATION_ID_LIVE, notification)
            }
        } catch (_: Throwable) {
            // Fallback to direct notification update if background FGS restriction applies
            TimerNotificationHelper.updateOrShowLiveNotification(
                context = this,
                mode = mode,
                status = status,
                secondsRemaining = secondsRemaining,
                totalSeconds = totalSeconds,
                activeTaskTitle = taskTitle,
                roundInfo = roundInfo
            )
        }

        return START_STICKY
    }

    companion object {
        const val ACTION_START_OR_UPDATE = "com.example.notification.START_OR_UPDATE"
        const val ACTION_STOP_SERVICE = "com.example.notification.STOP_SERVICE"

        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_SECONDS_REMAINING = "extra_seconds_remaining"
        const val EXTRA_TOTAL_SECONDS = "extra_total_seconds"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_ROUND_INFO = "extra_round_info"

        fun startOrUpdate(
            context: Context,
            mode: TimerMode,
            status: TimerStatus,
            secondsRemaining: Int,
            totalSeconds: Int,
            activeTaskTitle: String?,
            roundInfo: String
        ) {
            // Always update the notification immediately via NotificationManager
            TimerNotificationHelper.updateOrShowLiveNotification(
                context = context,
                mode = mode,
                status = status,
                secondsRemaining = secondsRemaining,
                totalSeconds = totalSeconds,
                activeTaskTitle = activeTaskTitle,
                roundInfo = roundInfo
            )

            // Also start foreground service when running to keep timer alive in background
            try {
                val intent = Intent(context, TimerForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_MODE, mode.name)
                    putExtra(EXTRA_STATUS, status.name)
                    putExtra(EXTRA_SECONDS_REMAINING, secondsRemaining)
                    putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
                    putExtra(EXTRA_TASK_TITLE, activeTaskTitle)
                    putExtra(EXTRA_ROUND_INFO, roundInfo)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && status == TimerStatus.RUNNING) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {}
        }

        fun stop(context: Context) {
            TimerNotificationHelper.cancelLiveNotification(context)
            try {
                val intent = Intent(context, TimerForegroundService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.stopService(intent)
            } catch (_: Throwable) {}
        }
    }
}
