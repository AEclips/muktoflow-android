package com.example.data.pomofocus

import com.example.data.model.TaskEntity
import kotlin.math.max

/** Represents an authenticated Pomofocus user. */
data class PomofocusUser(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val token: String = ""
)

/** Represents a single task in Pomofocus.io. */
data class PomofocusTask(
    val id: String = "",
    val title: String,
    val actPomodoro: Int = 0,
    val estPomodoro: Int = 1,
    val done: Boolean = false,
    val note: String = "",
    val projectName: String = ""
)

/** UI state for Pomofocus Account Connection & Sync. */
data class PomofocusSyncState(
    val isConnected: Boolean = false,
    val userEmail: String? = null,
    val userName: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncedAt: Long = 0L,
    val lastSyncMessage: String? = null,
    val autoSyncEnabled: Boolean = true
)

/** Result of a bidirectional merge between local Room tasks and remote Pomofocus tasks. */
data class TaskMergeResult(
    val tasksToInsertLocally: List<TaskEntity>,
    val tasksToUpdateLocally: List<TaskEntity>,
    val tasksToPushToRemote: List<TaskEntity>,
    val tasksToUpdateOnRemote: List<PomofocusTask>
) {
    val totalChanges: Int
        get() = tasksToInsertLocally.size + tasksToUpdateLocally.size +
                tasksToPushToRemote.size + tasksToUpdateOnRemote.size
}

object PomofocusTaskMerger {
    /**
     * Bidirectional smart merge:
     * - Tasks existing in only one place are added to the other.
     * - For matching tasks (by normalized title), higher completed count and latest completion win.
     */
    fun merge(
        localTasks: List<TaskEntity>,
        remoteTasks: List<PomofocusTask>
    ): TaskMergeResult {
        val toInsertLocally = mutableListOf<TaskEntity>()
        val toUpdateLocally = mutableListOf<TaskEntity>()
        val toPushToRemote = mutableListOf<TaskEntity>()
        val toUpdateOnRemote = mutableListOf<PomofocusTask>()

        val remoteMap = remoteTasks.associateBy { it.title.trim().lowercase() }
        val localMap = localTasks.associateBy { it.title.trim().lowercase() }

        // Process remote tasks
        for (remote in remoteTasks) {
            val key = remote.title.trim().lowercase()
            if (key.isBlank()) continue

            val localMatch = localMap[key]
            if (localMatch == null) {
                // New task from Pomofocus -> insert into local DB
                toInsertLocally.add(
                    TaskEntity(
                        title = remote.title.trim(),
                        estimatedPomodoros = remote.estPomodoro.coerceAtLeast(1),
                        completedPomodoros = remote.actPomodoro.coerceAtLeast(0),
                        isCompleted = remote.done,
                        notes = remote.note,
                        tag = remote.projectName.ifBlank { "Pomofocus" }
                    )
                )
            } else {
                // Task exists in both -> smart merge
                val mergedAct = max(localMatch.completedPomodoros, remote.actPomodoro)
                val mergedDone = localMatch.isCompleted || remote.done
                val mergedEst = max(localMatch.estimatedPomodoros, remote.estPomodoro)

                if (mergedAct != localMatch.completedPomodoros || mergedDone != localMatch.isCompleted || mergedEst != localMatch.estimatedPomodoros) {
                    toUpdateLocally.add(
                        localMatch.copy(
                            completedPomodoros = mergedAct,
                            isCompleted = mergedDone,
                            estimatedPomodoros = mergedEst
                        )
                    )
                }

                if (mergedAct != remote.actPomodoro || mergedDone != remote.done || mergedEst != remote.estPomodoro) {
                    toUpdateOnRemote.add(
                        remote.copy(
                            actPomodoro = mergedAct,
                            done = mergedDone,
                            estPomodoro = mergedEst
                        )
                    )
                }
            }
        }

        // Process local tasks not in remote
        for (local in localTasks) {
            val key = local.title.trim().lowercase()
            if (key.isBlank()) continue

            if (!remoteMap.containsKey(key)) {
                toPushToRemote.add(local)
            }
        }

        return TaskMergeResult(
            tasksToInsertLocally = toInsertLocally,
            tasksToUpdateLocally = toUpdateLocally,
            tasksToPushToRemote = toPushToRemote,
            tasksToUpdateOnRemote = toUpdateOnRemote
        )
    }
}
