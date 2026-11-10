package com.example.data.pomofocus

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Handles account authentication, session preservation, and bidirectional task syncing
 * between MuktoFlow's local Room database and Pomofocus.io.
 *
 * Uses direct HTTPS REST communication with Pomofocus.io's private API using the
 * user's access_token and X-Client-Version headers, with WebView IndexedDB as a fallback.
 */
class PomofocusSyncEngine(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pomofocus_sync_prefs", Context.MODE_PRIVATE)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncState = MutableStateFlow(loadInitialState())
    val syncState: StateFlow<PomofocusSyncState> = _syncState.asStateFlow()

    private var headlessWebView: WebView? = null
    private var isHeadlessInitialized = false

    private fun loadInitialState(): PomofocusSyncState {
        val isConnected = prefs.getBoolean(PREF_IS_CONNECTED, false)
        val email = prefs.getString(PREF_USER_EMAIL, null)
        val name = prefs.getString(PREF_USER_NAME, null)
        val lastSynced = prefs.getLong(PREF_LAST_SYNCED, 0L)
        val autoSync = prefs.getBoolean(PREF_AUTO_SYNC, true)
        return PomofocusSyncState(
            isConnected = isConnected,
            userEmail = email,
            userName = name,
            lastSyncedAt = lastSynced,
            autoSyncEnabled = autoSync
        )
    }

    fun getAccessToken(): String? {
        val saved = prefs.getString(PREF_ACCESS_TOKEN, null)
        if (!saved.isNullOrBlank()) return saved
        // Try extracting from CookieManager
        val cookie = try {
            CookieManager.getInstance().getCookie(POMOFOCUS_BASE_URL)
        } catch (_: Throwable) { null }
        return extractTokenFromCookieString(cookie)
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_SYNC, enabled).apply()
        _syncState.value = _syncState.value.copy(autoSyncEnabled = enabled)
    }

    /** Called when the user successfully signs in via the In-App Auth Sheet. */
    fun saveAuthenticatedUser(user: PomofocusUser) {
        val tokenToSave = if (user.token.isNotBlank()) user.token else (getAccessToken() ?: "")
        prefs.edit()
            .putBoolean(PREF_IS_CONNECTED, true)
            .putString(PREF_USER_ID, user.id)
            .putString(PREF_USER_EMAIL, user.email)
            .putString(PREF_USER_NAME, user.name)
            .putString(PREF_ACCESS_TOKEN, tokenToSave)
            .apply()

        _syncState.value = _syncState.value.copy(
            isConnected = true,
            userEmail = user.email.ifBlank { "Pomofocus Account" },
            userName = user.name,
            lastSyncMessage = "Account connected: ${user.email.ifBlank { "Pomofocus" }}"
        )
    }

    /** Disconnect account and clear session cookies. */
    fun disconnect() {
        prefs.edit()
            .putBoolean(PREF_IS_CONNECTED, false)
            .remove(PREF_USER_ID)
            .remove(PREF_USER_EMAIL)
            .remove(PREF_USER_NAME)
            .remove(PREF_ACCESS_TOKEN)
            .remove(PREF_LAST_SYNCED)
            .apply()

        mainHandler.post {
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                headlessWebView?.destroy()
                headlessWebView = null
                isHeadlessInitialized = false
            } catch (_: Throwable) {}
        }

        _syncState.value = PomofocusSyncState(
            isConnected = false,
            userEmail = null,
            userName = null,
            lastSyncedAt = 0L,
            lastSyncMessage = "Disconnected from Pomofocus"
        )
    }

    /**
     * Fetches user profile from /api/user with the given token.
     */
    suspend fun verifyUserProfile(token: String): PomofocusUser? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$POMOFOCUS_BASE_URL/api/user")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", token)
                setRequestProperty("X-Client-Version", CLIENT_VERSION)
                setRequestProperty("X-Requested-With", "XMLHttpRequest")
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                val id = obj.optString("_id", obj.optString("id", ""))
                val email = obj.optString("email", "")
                val name = obj.optString("name", obj.optString("username", "Pomofocus User"))
                return@withContext PomofocusUser(id = id, email = email, name = name, token = token)
            }
        } catch (_: Throwable) {}
        null
    }

    /**
     * Fetches current tasks from Pomofocus via its REST /api/todos endpoint or IndexedDB fallback.
     */
    fun fetchRemoteTasks(onResult: (List<PomofocusTask>?, String?) -> Unit) {
        val token = getAccessToken()

        scope.launch {
            if (!token.isNullOrBlank()) {
                try {
                    val url = URL("$POMOFOCUS_BASE_URL/api/todos")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("Authorization", token)
                        setRequestProperty("X-Client-Version", CLIENT_VERSION)
                        setRequestProperty("X-Requested-With", "XMLHttpRequest")
                        setRequestProperty("User-Agent", USER_AGENT)
                        connectTimeout = 10000
                        readTimeout = 10000
                    }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val tasks = parseTasksJson(body)
                        mainHandler.post { onResult(tasks, null) }
                        return@launch
                    }
                } catch (_: Throwable) {}
            }

            // Fallback to Headless WebView
            fetchViaHeadlessWebView(token, onResult)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureHeadlessWebView(onReady: (WebView) -> Unit) {
        mainHandler.post {
            val existing = headlessWebView
            if (existing != null && isHeadlessInitialized) {
                onReady(existing)
                return@post
            }

            try {
                val wv = existing ?: WebView(context).also { headlessWebView = it }
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    userAgentString = USER_AGENT
                }

                val cm = CookieManager.getInstance()
                cm.setAcceptCookie(true)
                try { cm.setAcceptThirdPartyCookies(wv, true) } catch (_: Throwable) {}

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isHeadlessInitialized = true
                        onReady(wv)
                    }
                }
                wv.loadUrl(POMOFOCUS_APP_URL)
            } catch (e: Throwable) {
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastSyncMessage = "WebView initialization failed: ${e.message}"
                )
            }
        }
    }

    private fun fetchViaHeadlessWebView(token: String?, onResult: (List<PomofocusTask>?, String?) -> Unit) {
        ensureHeadlessWebView { wv ->
            val authHeader = if (!token.isNullOrBlank()) "'Authorization': ${JSONObject.quote(token)}," else ""
            val js = """
                (function() {
                    var token = ${if (!token.isNullOrBlank()) JSONObject.quote(token) else "null"};
                    if (!token) {
                        var match = document.cookie.match(/(?:^|;\s*)access_token=([^;]+)/);
                        if (match && match[1]) token = decodeURIComponent(match[1]);
                    }

                    var headers = {
                        'X-Client-Version': '$CLIENT_VERSION',
                        'X-Requested-With': 'XMLHttpRequest'
                    };
                    if (token) headers['Authorization'] = token;

                    fetch('/api/todos', {
                        credentials: 'include',
                        headers: headers
                    })
                    .then(function(r) {
                        if (r.ok) return r.json();
                        throw new Error('HTTP ' + r.status);
                    })
                    .then(function(data) {
                        window.PomofocusBridge.onTasksFetched(JSON.stringify(data), null);
                    })
                    .catch(function(err) {
                        try {
                            var req = indexedDB.open('pomofocus', 23);
                            req.onsuccess = function(e) {
                                var db = e.target.result;
                                if (db.objectStoreNames.contains('activity')) {
                                    var tx = db.transaction('activity', 'readonly');
                                    var getAll = tx.objectStore('activity').getAll();
                                    getAll.onsuccess = function() {
                                        window.PomofocusBridge.onTasksFetched(JSON.stringify(getAll.result || []), null);
                                    };
                                    getAll.onerror = function() {
                                        window.PomofocusBridge.onTasksFetched(null, err.message || 'IDB error');
                                    };
                                } else {
                                    window.PomofocusBridge.onTasksFetched('[]', null);
                                }
                            };
                            req.onerror = function() {
                                window.PomofocusBridge.onTasksFetched(null, err.message || 'IDB open error');
                            };
                        } catch(e) {
                            window.PomofocusBridge.onTasksFetched(null, err.message || 'Fetch failed');
                        }
                    });
                })();
            """.trimIndent()

            val bridge = object {
                @JavascriptInterface
                fun onTasksFetched(json: String?, error: String?) {
                    mainHandler.post {
                        if (json != null) {
                            val tasks = parseTasksJson(json)
                            onResult(tasks, null)
                        } else {
                            onResult(null, error)
                        }
                    }
                }
            }

            wv.addJavascriptInterface(bridge, "PomofocusBridge")
            wv.evaluateJavascript(js, null)
        }
    }

    /**
     * Pushes a new task to Pomofocus via POST /api/todo.
     */
    fun pushTaskToRemote(task: TaskEntity, onDone: (Boolean) -> Unit) {
        val token = getAccessToken()

        scope.launch {
            if (!token.isNullOrBlank()) {
                try {
                    val url = URL("$POMOFOCUS_BASE_URL/api/todo")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Authorization", token)
                        setRequestProperty("X-Client-Version", CLIENT_VERSION)
                        setRequestProperty("X-Requested-With", "XMLHttpRequest")
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("User-Agent", USER_AGENT)
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    val json = JSONObject().apply {
                        put("title", task.title)
                        put("estPomodoro", task.estimatedPomodoros)
                        put("actPomodoro", task.completedPomodoros)
                        put("done", if (task.isCompleted) 1 else 0)
                        put("note", task.notes)
                        put("projectName", task.tag)
                    }
                    OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        mainHandler.post { onDone(true) }
                        return@launch
                    }
                } catch (_: Throwable) {}
            }

            // Fallback to Headless WebView
            ensureHeadlessWebView { wv ->
                val json = JSONObject().apply {
                    put("title", task.title)
                    put("estPomodoro", task.estimatedPomodoros)
                    put("actPomodoro", task.completedPomodoros)
                    put("done", if (task.isCompleted) 1 else 0)
                    put("note", task.notes)
                    put("projectName", task.tag)
                }.toString()

                val js = """
                    (function() {
                        var match = document.cookie.match(/(?:^|;\s*)access_token=([^;]+)/);
                        var token = match ? decodeURIComponent(match[1]) : null;
                        var headers = {
                            'Content-Type': 'application/json',
                            'X-Client-Version': '$CLIENT_VERSION',
                            'X-Requested-With': 'XMLHttpRequest'
                        };
                        if (token) headers['Authorization'] = token;

                        fetch('/api/todo', {
                            method: 'POST',
                            headers: headers,
                            credentials: 'include',
                            body: JSON.stringify($json)
                        }).catch(function() {});
                    })();
                """.trimIndent()
                wv.evaluateJavascript(js) { onDone(true) }
            }
        }
    }

    /**
     * Updates an existing task on Pomofocus via POST /api/todo/update.
     */
    fun updateTaskOnRemote(task: PomofocusTask, onDone: (Boolean) -> Unit) {
        val token = getAccessToken()

        scope.launch {
            if (!token.isNullOrBlank() && task.id.isNotBlank()) {
                try {
                    val url = URL("$POMOFOCUS_BASE_URL/api/todo/update")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Authorization", token)
                        setRequestProperty("X-Client-Version", CLIENT_VERSION)
                        setRequestProperty("X-Requested-With", "XMLHttpRequest")
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("User-Agent", USER_AGENT)
                        connectTimeout = 8000
                        readTimeout = 8000
                    }
                    val json = JSONObject().apply {
                        put("id", task.id)
                        put("title", task.title)
                        put("estPomodoro", task.estPomodoro)
                        put("actPomodoro", task.actPomodoro)
                        put("done", if (task.done) 1 else 0)
                        put("note", task.note)
                        put("projectName", task.projectName)
                    }
                    OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }
                    val code = conn.responseCode
                    if (code in 200..299) {
                        mainHandler.post { onDone(true) }
                        return@launch
                    }
                } catch (_: Throwable) {}
            }

            // Fallback to Headless WebView
            ensureHeadlessWebView { wv ->
                val json = JSONObject().apply {
                    if (task.id.isNotBlank()) put("id", task.id)
                    put("title", task.title)
                    put("estPomodoro", task.estPomodoro)
                    put("actPomodoro", task.actPomodoro)
                    put("done", if (task.done) 1 else 0)
                    put("note", task.note)
                    put("projectName", task.projectName)
                }.toString()

                val js = """
                    (function() {
                        var match = document.cookie.match(/(?:^|;\s*)access_token=([^;]+)/);
                        var token = match ? decodeURIComponent(match[1]) : null;
                        var headers = {
                            'Content-Type': 'application/json',
                            'X-Client-Version': '$CLIENT_VERSION',
                            'X-Requested-With': 'XMLHttpRequest'
                        };
                        if (token) headers['Authorization'] = token;

                        fetch('/api/todo/update', {
                            method: 'POST',
                            headers: headers,
                            credentials: 'include',
                            body: JSON.stringify($json)
                        }).catch(function(e) {});
                    })();
                """.trimIndent()
                wv.evaluateJavascript(js) { onDone(true) }
            }
        }
    }

    /**
     * Executes bidirectional smart merge:
     * - Fetches remote tasks
     * - Merges with local tasks
     * - Inserts/Updates local DB
     * - Pushes/Updates remote tasks
     */
    fun performFullSync(
        localTasks: List<TaskEntity>,
        insertLocal: suspend (TaskEntity) -> Long,
        updateLocal: suspend (TaskEntity) -> Unit,
        onComplete: (success: Boolean, message: String) -> Unit
    ) {
        _syncState.value = _syncState.value.copy(isSyncing = true, lastSyncMessage = "Syncing with Pomofocus...")

        fetchRemoteTasks { remoteTasks, error ->
            if (remoteTasks == null) {
                val msg = error ?: "Could not connect to Pomofocus"
                _syncState.value = _syncState.value.copy(isSyncing = false, lastSyncMessage = msg)
                onComplete(false, msg)
                return@fetchRemoteTasks
            }

            scope.launch {
                try {
                    val mergeResult = PomofocusTaskMerger.merge(localTasks, remoteTasks)

                    // 1. Insert newly discovered remote tasks into Room DB
                    for (task in mergeResult.tasksToInsertLocally) {
                        insertLocal(task)
                    }

                    // 2. Update local tasks that progressed remotely
                    for (task in mergeResult.tasksToUpdateLocally) {
                        updateLocal(task)
                    }

                    // 3. Push new local tasks to Pomofocus
                    for (task in mergeResult.tasksToPushToRemote) {
                        pushTaskToRemote(task) {}
                    }

                    // 4. Update remote tasks that progressed locally
                    for (task in mergeResult.tasksToUpdateOnRemote) {
                        updateTaskOnRemote(task) {}
                    }

                    val now = System.currentTimeMillis()
                    prefs.edit().putLong(PREF_LAST_SYNCED, now).apply()

                    val summary = if (mergeResult.totalChanges == 0) {
                        "All tasks are up to date (${remoteTasks.size} remote tasks)"
                    } else {
                        "Synced: +${mergeResult.tasksToInsertLocally.size} local, +${mergeResult.tasksToPushToRemote.size} remote"
                    }

                    _syncState.value = _syncState.value.copy(
                        isSyncing = false,
                        lastSyncedAt = now,
                        lastSyncMessage = summary
                    )
                    onComplete(true, summary)
                } catch (e: Throwable) {
                    val msg = "Sync failed: ${e.message}"
                    _syncState.value = _syncState.value.copy(isSyncing = false, lastSyncMessage = msg)
                    onComplete(false, msg)
                }
            }
        }
    }

    companion object {
        const val POMOFOCUS_BASE_URL = "https://pomofocus.io"
        const val POMOFOCUS_APP_URL = "https://pomofocus.io/app"
        const val POMOFOCUS_LOGIN_URL = "https://pomofocus.io/login"
        const val CLIENT_VERSION = "1.0.1"
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

        private const val PREF_IS_CONNECTED = "pomofocus_is_connected"
        private const val PREF_USER_ID = "pomofocus_user_id"
        private const val PREF_USER_EMAIL = "pomofocus_user_email"
        private const val PREF_USER_NAME = "pomofocus_user_name"
        private const val PREF_ACCESS_TOKEN = "pomofocus_access_token"
        private const val PREF_LAST_SYNCED = "pomofocus_last_synced"
        private const val PREF_AUTO_SYNC = "pomofocus_auto_sync"

        fun extractTokenFromCookieString(cookieHeader: String?): String? {
            if (cookieHeader.isNullOrBlank()) return null
            val cookies = cookieHeader.split(";")
            for (c in cookies) {
                val parts = c.trim().split("=", limit = 2)
                if (parts.size == 2 && parts[0].trim() == "access_token") {
                    return try {
                        URLDecoder.decode(parts[1].trim(), "UTF-8")
                    } catch (_: Throwable) {
                        parts[1].trim()
                    }
                }
            }
            return null
        }

        fun parseTasksJson(jsonStr: String): List<PomofocusTask> {
            val list = mutableListOf<PomofocusTask>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id", obj.optString("_id", ""))
                    val title = obj.optString("title", obj.optString("name", "")).trim()
                    if (title.isEmpty()) continue
                    val act = obj.optInt("actPomodoro", obj.optInt("act", obj.optInt("actPomodoros", 0)))
                    val est = obj.optInt("estPomodoro", obj.optInt("est", obj.optInt("estPomodoros", 1)))
                    val done = obj.optBoolean("done", obj.optBoolean("completed", obj.optInt("done", 0) == 1))
                    val note = obj.optString("note", "")
                    val projectName = obj.optString("projectName", obj.optString("project", ""))
                    list.add(
                        PomofocusTask(
                            id = id,
                            title = title,
                            actPomodoro = act,
                            estPomodoro = est,
                            done = done,
                            note = note,
                            projectName = projectName
                        )
                    )
                }
            } catch (_: Throwable) {}
            return list
        }
    }
}
