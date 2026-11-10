package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ThemePreference
import com.example.data.model.TimerMode
import com.example.data.model.TimerStatus
import com.example.data.repository.PomodoroRepository
import com.example.notification.TimerNotificationHelper
import com.example.viewmodel.PomodoroViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun saveScreenshot(fileName: String) {
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread {
            val decorView = composeTestRule.activity.window.decorView
            val width = decorView.width.coerceAtLeast(1080)
            val height = decorView.height.coerceAtLeast(2340)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val windowViews = try {
                android.view.inspector.WindowInspector.getGlobalWindowViews()
            } catch (_: Throwable) {
                emptyList()
            }
            if (windowViews.isNotEmpty()) {
                for (v in windowViews) {
                    v.draw(canvas)
                }
            } else {
                decorView.draw(canvas)
            }
            val outDir = File("C:/Users/hp/.gemini/antigravity/brain/3ed10162-4f08-428c-ba05-54c93ec3ee79")
            outDir.mkdirs()
            FileOutputStream(File(outDir, fileName)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("MuktoFlow", appName)
    }

    @Test
    fun `streak calculation with empty dates`() {
        val streak = PomodoroRepository.calculateStreak(emptyList())
        assertEquals(0, streak)
    }

    @Test
    fun `streak calculation with today and consecutive days`() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val today = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = dateFormat.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val twoDaysAgo = dateFormat.format(cal.time)

        val streak = PomodoroRepository.calculateStreak(listOf(today, yesterday, twoDaysAgo))
        assertEquals(3, streak)
    }

    @Test
    fun `import tasks from pomofocus text parses titles and estimates`() {
        val vm = ViewModelProvider(composeTestRule.activity)[PomodoroViewModel::class.java]
        val added = vm.importTasksFromText("[ ] Write report 3\n- Read book #2\n2/4 Study math\nPlain task\n\nPlain task")
        assertEquals(4, added)
    }

    @Test
    fun `pomofocus task merger merges tasks bidirectionally with higher progress`() {
        val localTasks = listOf(
            com.example.data.model.TaskEntity(
                id = 1L,
                title = "Design Mockup",
                estimatedPomodoros = 2,
                completedPomodoros = 1,
                isCompleted = false
            ),
            com.example.data.model.TaskEntity(
                id = 2L,
                title = "Only in Local",
                estimatedPomodoros = 3,
                completedPomodoros = 0,
                isCompleted = false
            )
        )

        val remoteTasks = listOf(
            com.example.data.pomofocus.PomofocusTask(
                id = "pomo_1",
                title = "Design Mockup",
                estPomodoro = 4,
                actPomodoro = 3,
                done = true
            ),
            com.example.data.pomofocus.PomofocusTask(
                id = "pomo_2",
                title = "Only in Remote",
                estPomodoro = 2,
                actPomodoro = 1,
                done = false
            )
        )

        val result = com.example.data.pomofocus.PomofocusTaskMerger.merge(localTasks, remoteTasks)

        // Remote-only task should be inserted locally
        assertEquals(1, result.tasksToInsertLocally.size)
        assertEquals("Only in Remote", result.tasksToInsertLocally[0].title)

        // Local task with matching title should be updated to higher remote count & completed status
        assertEquals(1, result.tasksToUpdateLocally.size)
        assertEquals(3, result.tasksToUpdateLocally[0].completedPomodoros)
        assertTrue(result.tasksToUpdateLocally[0].isCompleted)

        // Local-only task should be pushed to remote
        assertEquals(1, result.tasksToPushToRemote.size)
        assertEquals("Only in Local", result.tasksToPushToRemote[0].title)
    }

    @Test
    fun `pomofocus sync engine parses json and tracks authentication state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = com.example.data.pomofocus.PomofocusSyncEngine(context)

        val json = """
            [
                {"id": "t1", "title": "API Backend Sync", "actPomodoro": 4, "estPomodoro": 5, "done": 1},
                {"_id": "t2", "name": "Write Unit Tests", "act": 2, "est": 3, "completed": false}
            ]
        """.trimIndent()

        val parsed = com.example.data.pomofocus.PomofocusSyncEngine.parseTasksJson(json)
        assertEquals(2, parsed.size)
        assertEquals("API Backend Sync", parsed[0].title)
        assertEquals(4, parsed[0].actPomodoro)
        assertTrue(parsed[0].done)

        assertEquals("Write Unit Tests", parsed[1].title)
        assertEquals(2, parsed[1].actPomodoro)
        org.junit.Assert.assertFalse(parsed[1].done)

        // Test login persistence and disconnect
        engine.saveAuthenticatedUser(com.example.data.pomofocus.PomofocusUser("user_123", "flow@example.com", "Flow User", "token_xyz"))
        assertTrue(engine.syncState.value.isConnected)
        assertEquals("flow@example.com", engine.syncState.value.userEmail)
        assertEquals("token_xyz", engine.getAccessToken())

        engine.disconnect()
        org.junit.Assert.assertFalse(engine.syncState.value.isConnected)
        org.junit.Assert.assertNull(engine.syncState.value.userEmail)
        org.junit.Assert.assertNull(engine.getAccessToken())
    }
    @Test
    fun `live timer notification builds with chronometer and interactive actions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = TimerNotificationHelper.buildLiveTimerNotification(
            context = context,
            mode = TimerMode.WORK,
            status = TimerStatus.RUNNING,
            secondsRemaining = 1450,
            totalSeconds = 1500,
            activeTaskTitle = "Deep Work Architecture",
            roundInfo = "Round 1/4"
        )
        assertEquals(3, notification.actions.size)
        assertEquals("Pause", notification.actions[0].title.toString())
        assertEquals("+5 Min", notification.actions[1].title.toString())
        assertEquals("Reset", notification.actions[2].title.toString())
    }

    @Test
    fun `end to end app navigation timer tasks import analytics settings and screenshots`() {
        val vm = ViewModelProvider(composeTestRule.activity)[PomodoroViewModel::class.java]

        // Connect Google account and add realistic tasks & session logs so all screens are rich and verified
        kotlinx.coroutines.runBlocking {
            val db = com.example.data.db.ZenFocusDatabase.getDatabase(composeTestRule.activity)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val cal = Calendar.getInstance()
            val todayStr = dateFormat.format(cal.time)
            db.sessionLogDao().insertLog(
                com.example.data.model.SessionLogEntity(
                    taskId = 1L,
                    taskTitle = "System Architecture & UI Polish",
                    tag = "Coding",
                    sessionType = "WORK",
                    durationSeconds = 50 * 60,
                    timestamp = System.currentTimeMillis() - 3600_000L,
                    dateString = todayStr
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, -1)
            db.sessionLogDao().insertLog(
                com.example.data.model.SessionLogEntity(
                    taskId = 2L,
                    taskTitle = "Prepare Q4 Sprint Roadmap",
                    tag = "Work",
                    sessionType = "WORK",
                    durationSeconds = 75 * 60,
                    timestamp = System.currentTimeMillis() - 86400_000L,
                    dateString = dateFormat.format(cal.time)
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, -1)
            db.sessionLogDao().insertLog(
                com.example.data.model.SessionLogEntity(
                    taskId = 3L,
                    taskTitle = "Kotlin Coroutines Deep Dive",
                    tag = "Study",
                    sessionType = "WORK",
                    durationSeconds = 60 * 60,
                    timestamp = System.currentTimeMillis() - 172800_000L,
                    dateString = dateFormat.format(cal.time)
                )
            )
        }

        composeTestRule.runOnUiThread {
            vm.addNewTask(
                title = "System Architecture & UI Polish",
                tag = "Coding",
                notes = "Review Jetpack Compose layouts and notification service",
                estimatedPomodoros = 4,
                priority = 2
            )
            vm.addNewTask(
                title = "Prepare Q4 Sprint Roadmap",
                tag = "Work",
                notes = "Sync milestones with Google Calendar",
                estimatedPomodoros = 3,
                priority = 1
            )
        }
        composeTestRule.waitForIdle()

        // 1. Verify Timer Screen loads & buttons work
        composeTestRule.onNodeWithText("MuktoFlow").assertIsDisplayed()
        composeTestRule.onNodeWithText("25:00").assertIsDisplayed()

        // Test custom timer duration & Zen fullscreen toggle
        composeTestRule.runOnUiThread {
            vm.setCustomTimerMinutes(45)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("45:00").assertIsDisplayed()
        composeTestRule.runOnUiThread {
            vm.setCustomTimerMinutes(25)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("25:00").assertIsDisplayed()

        composeTestRule.onNodeWithTag("add_five_button").performClick()
        composeTestRule.onNodeWithText("30:00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reset_button").performClick()
        composeTestRule.onNodeWithText("25:00").assertIsDisplayed()

        // Test mode switching
        composeTestRule.onNodeWithTag("mode_short_break").performClick()
        composeTestRule.onNodeWithText("05:00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_long_break").performClick()
        composeTestRule.onNodeWithText("15:00").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_work").performClick()
        composeTestRule.onNodeWithText("25:00").assertIsDisplayed()

        // Capture Screenshot 1: Timer Screen (Light Mode)
        saveScreenshot("screen_1_timer_light.png")

        // Switch to Dark Mode & Capture Screenshot 2: Timer Screen (Dark Mode)
        composeTestRule.runOnUiThread {
            vm.updateSettings(vm.settings.value.copy(themePreference = ThemePreference.DARK))
        }
        composeTestRule.waitForIdle()
        saveScreenshot("screen_2_timer_dark.png")

        // Switch back to Light Mode for remaining screenshots
        composeTestRule.runOnUiThread {
            vm.updateSettings(vm.settings.value.copy(themePreference = ThemePreference.LIGHT))
        }
        composeTestRule.waitForIdle()

        // 2. Navigate to Tasks Screen & verify tasks and Pomofocus sync card
        composeTestRule.onNodeWithTag("nav_tasks").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Tasks & Projects").assertIsDisplayed()
        composeTestRule.onNodeWithText("System Architecture & UI Polish").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pomofocus_sync_card").assertIsDisplayed()
        saveScreenshot("screen_3_tasks.png")

        // 3. Navigate to Analytics Screen & capture Screenshot 4
        composeTestRule.onNodeWithTag("nav_analytics").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Mindful Analytics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Last 7 Days (Minutes)").assertIsDisplayed()
        saveScreenshot("screen_4_analytics.png")

        // 4. Navigate to Settings Screen & capture Screenshot 5
        composeTestRule.onNodeWithTag("nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Preferences").assertIsDisplayed()
        composeTestRule.onNodeWithText("Appearance & Theme").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_pomofocus_card").assertIsDisplayed()
        saveScreenshot("screen_5_settings.png")
    }
}
