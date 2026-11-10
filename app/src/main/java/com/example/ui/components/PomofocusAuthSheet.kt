package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.pomofocus.PomofocusSyncEngine
import com.example.data.pomofocus.PomofocusUser
import com.example.ui.theme.focusAccentColor
import com.example.viewmodel.PomodoroViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PomofocusAuthSheet(
    viewModel: PomodoroViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accent = focusAccentColor()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var progress by remember { mutableFloatStateOf(0f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var statusText by remember { mutableStateOf("Sign in with Google or Email on the page") }

    fun handleLoginSuccess(user: PomofocusUser) {
        viewModel.onPomofocusLoginSuccess(user)
        Toast.makeText(context, "Connected as ${user.email.ifBlank { "Pomofocus User" }}", Toast.LENGTH_SHORT).show()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("pomofocus_auth_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
        ) {
            // Header Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect Pomofocus.io",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }

                    IconButton(onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            if (progress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = accent
                )
            }

            // WebView container
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                userAgentString = PomofocusSyncEngine.USER_AGENT
                            }

                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            try { cm.setAcceptThirdPartyCookies(this, true) } catch (_: Throwable) {}

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                    if (newProgress > 60) {
                                        injectAuthObserver(view)
                                    }
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    transport?.webView = view
                                    resultMsg?.sendToTarget()
                                    return true
                                }
                            }

                            val authBridge = object {
                                @JavascriptInterface
                                fun onLoginDetected(id: String, email: String, name: String, token: String) {
                                    post {
                                        val user = PomofocusUser(id = id, email = email, name = name, token = token)
                                        handleLoginSuccess(user)
                                    }
                                }
                            }
                            addJavascriptInterface(authBridge, "MuktoFlowAuth")

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    injectAuthObserver(view)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val targetUrl = request?.url?.toString() ?: ""
                                    if (targetUrl.contains("accounts.google.com") || targetUrl.contains("pomofocus.io")) {
                                        return false
                                    }
                                    return false
                                }
                            }

                            loadUrl(PomofocusSyncEngine.POMOFOCUS_LOGIN_URL)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom Action Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tasks sync automatically once logged in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val cm = CookieManager.getInstance()
                            val cookie = cm.getCookie(PomofocusSyncEngine.POMOFOCUS_BASE_URL)
                            val token = PomofocusSyncEngine.extractTokenFromCookieString(cookie)

                            if (!token.isNullOrBlank()) {
                                statusText = "Verifying Pomofocus account..."
                                scope.launch {
                                    val user = viewModel.pomofocusSyncEngine.verifyUserProfile(token)
                                    if (user != null) {
                                        handleLoginSuccess(user)
                                    } else {
                                        handleLoginSuccess(PomofocusUser(email = "Pomofocus Account", token = token))
                                    }
                                }
                            } else {
                                // Try checking via Javascript
                                webViewRef?.evaluateJavascript(
                                    """
                                    (function() {
                                        var match = document.cookie.match(/(?:^|;\s*)access_token=([^;]+)/);
                                        if (match && match[1]) {
                                            var tok = decodeURIComponent(match[1]);
                                            fetch('/api/user', {
                                                headers: {
                                                    'Authorization': tok,
                                                    'X-Client-Version': '${PomofocusSyncEngine.CLIENT_VERSION}',
                                                    'X-Requested-With': 'XMLHttpRequest'
                                                }
                                            })
                                            .then(function(r) { return r.ok ? r.json() : null; })
                                            .then(function(u) {
                                                window.MuktoFlowAuth.onLoginDetected(
                                                    u ? (u._id || u.id || '') : '',
                                                    u ? (u.email || '') : 'Pomofocus Account',
                                                    u ? (u.name || '') : 'User',
                                                    tok
                                                );
                                            })
                                            .catch(function() {
                                                window.MuktoFlowAuth.onLoginDetected('', 'Pomofocus Account', 'User', tok);
                                            });
                                            return true;
                                        }
                                        return false;
                                    })();
                                    """.trimIndent()
                                ) { res ->
                                    if (res == null || res == "false" || res == "null") {
                                        Toast.makeText(context, "Please complete login on the page first.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("I'm Logged In", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Injects background script to monitor and capture Pomofocus access_token cookie. */
private fun injectAuthObserver(view: WebView?) {
    if (view == null) return
    val script = """
        (function() {
            if (window._muktoflow_poller_active) return;
            window._muktoflow_poller_active = true;

            function check() {
                var match = document.cookie.match(/(?:^|;\s*)access_token=([^;]+)/);
                if (match && match[1]) {
                    var token = decodeURIComponent(match[1]);
                    fetch('/api/user', {
                        headers: {
                            'Authorization': token,
                            'X-Client-Version': '${PomofocusSyncEngine.CLIENT_VERSION}',
                            'X-Requested-With': 'XMLHttpRequest'
                        }
                    })
                    .then(function(r) { return r.ok ? r.json() : null; })
                    .then(function(u) {
                        if (u) {
                            clearInterval(window._muktoflow_interval);
                            window.MuktoFlowAuth.onLoginDetected(
                                u._id || u.id || '',
                                u.email || '',
                                u.name || u.username || 'Pomofocus User',
                                token
                            );
                        }
                    })
                    .catch(function() {
                        clearInterval(window._muktoflow_interval);
                        window.MuktoFlowAuth.onLoginDetected('', 'Pomofocus Account', 'User', token);
                    });
                }
            }

            check();
            window._muktoflow_interval = setInterval(check, 1000);
        })();
    """.trimIndent()
    view.evaluateJavascript(script, null)
}
