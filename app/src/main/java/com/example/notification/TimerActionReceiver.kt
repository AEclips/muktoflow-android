package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TimerActionBus {
    private val _actions = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val actions: SharedFlow<String> = _actions.asSharedFlow()

    fun emitAction(action: String) {
        _actions.tryEmit(action)
    }
}

class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        TimerActionBus.emitAction(action)
    }
}
