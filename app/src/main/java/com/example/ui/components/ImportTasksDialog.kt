package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FocusColorLight
import com.example.viewmodel.PomodoroViewModel

/** Import dialog: paste a task list (e.g. copied from Pomofocus.io) – one task per line. */
@Composable
fun ImportTasksDialog(viewModel: PomodoroViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Tasks", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Copy your task list from Pomofocus.io (or any app) and paste it here. One task per line. Add a number at the end for estimated pomodoros, e.g. \"Write report 3\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Task one 2\nTask two 4") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .testTag("import_tasks_input")
                )
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!clip.isNullOrBlank()) text = clip
                }) { Text("Paste from clipboard") }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val n = viewModel.importTasksFromText(text)
                    Toast.makeText(context, if (n > 0) "Imported $n tasks" else "No new tasks found", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FocusColorLight),
                modifier = Modifier.testTag("import_tasks_confirm")
            ) { Text("Import", color = Color.White) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
