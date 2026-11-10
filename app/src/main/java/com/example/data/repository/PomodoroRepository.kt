package com.example.data.repository

import com.example.data.dao.SessionLogDao
import com.example.data.dao.TaskDao
import com.example.data.model.SessionLogEntity
import com.example.data.model.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PomodoroRepository(
    private val taskDao: TaskDao,
    private val sessionLogDao: SessionLogDao
) {
    val allTasks: Flow<List<TaskEntity>> = taskDao.getAllTasks()
    val incompleteTasks: Flow<List<TaskEntity>> = taskDao.getIncompleteTasks()
    val allLogs: Flow<List<SessionLogEntity>> = sessionLogDao.getAllLogs()
    val distinctWorkDates: Flow<List<String>> = sessionLogDao.getDistinctWorkDates()

    suspend fun getTaskById(id: Long): TaskEntity? = taskDao.getTaskById(id)

    suspend fun insertTask(task: TaskEntity): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: TaskEntity) = taskDao.updateTask(task)

    suspend fun deleteTask(task: TaskEntity) = taskDao.deleteTask(task)

    suspend fun deleteCompletedTasks() = taskDao.deleteCompletedTasks()

    suspend fun incrementTaskPomodoro(id: Long) = taskDao.incrementTaskPomodoro(id)

    suspend fun setTaskCompletion(id: Long, completed: Boolean) {
        val completedAt = if (completed) System.currentTimeMillis() else null
        taskDao.setTaskCompletion(id, completed, completedAt)
    }

    suspend fun logSession(
        taskId: Long?,
        taskTitle: String?,
        tag: String,
        sessionType: String,
        durationSeconds: Int
    ): Long {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val log = SessionLogEntity(
            taskId = taskId,
            taskTitle = taskTitle,
            tag = tag,
            sessionType = sessionType,
            durationSeconds = durationSeconds,
            timestamp = System.currentTimeMillis(),
            dateString = todayStr
        )
        val logId = sessionLogDao.insertLog(log)
        if (sessionType == "WORK" && taskId != null) {
            taskDao.incrementTaskPomodoro(taskId)
        }
        return logId
    }

    fun getTodayFocusSeconds(): Flow<Int?> {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return sessionLogDao.getFocusSecondsForDate(todayStr)
    }

    suspend fun clearLogs() {
        sessionLogDao.clearAllLogs()
    }

    companion object {
        fun calculateStreak(workDates: List<String>): Int {
            if (workDates.isEmpty()) return 0
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateSet = workDates.toSet()

            val calendar = Calendar.getInstance()
            var streak = 0

            val todayStr = dateFormat.format(calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = dateFormat.format(calendar.time)

            // If user did work today, streak starts from today.
            // If user didn't work today yet, but worked yesterday, streak is alive from yesterday!
            val checkCalendar = Calendar.getInstance()
            if (!dateSet.contains(todayStr)) {
                if (dateSet.contains(yesterdayStr)) {
                    checkCalendar.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    return 0
                }
            }

            while (true) {
                val dateStr = dateFormat.format(checkCalendar.time)
                if (dateSet.contains(dateStr)) {
                    streak++
                    checkCalendar.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
            return streak
        }
    }
}
