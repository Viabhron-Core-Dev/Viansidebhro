package com.example.service

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.AppDatabase
import com.example.data.SchedulerTask
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class SchedulerPageView(
    context: Context,
    private val serviceScope: CoroutineScope
) : FrameLayout(context) {

    private val db = AppDatabase.getDatabase(context)

    init {
        com.example.LogKeeper.writeLog("Scheduler", "Opened scheduler page")
        addView(ComposeView(context).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    SchedulerScreen(db, serviceScope)
                }
            }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SchedulerScreen(db: AppDatabase, scope: CoroutineScope) {
    val tasks by db.schedulerTaskDao().getAllTasks().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<SchedulerTask?>(null) }
    
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Scheduler", 
                style = MaterialTheme.typography.titleLarge, 
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { taskToEdit = task }
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(task.title, style = MaterialTheme.typography.titleMedium)
                                if (task.note.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(task.note, style = MaterialTheme.typography.bodyMedium)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    dateFormatter.format(Date(task.timeMillis)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Task")
        }
    }

    if (showAddDialog || taskToEdit != null) {
        val isEditing = taskToEdit != null
        var title by remember { mutableStateOf(taskToEdit?.title ?: "") }
        var note by remember { mutableStateOf(taskToEdit?.note ?: "") }
        
        // Simple time handling for now - just using offsets from now, ideally we'd use a DatePicker
        // For simplicity, we'll provide +1 hour, +1 day, +1 week options or simple text parsing
        // But let's just make it simple: text inputs or basic dropdown
        // Actually, just add a simple UI
        var daysOffset by remember { mutableStateOf("0") }
        var hoursOffset by remember { mutableStateOf("1") }
        
        Dialog(onDismissRequest = { 
            showAddDialog = false
            taskToEdit = null
        }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(if (isEditing) "Edit Task" else "Add Task", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (!isEditing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("When (from now):", style = MaterialTheme.typography.labelMedium)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = daysOffset,
                                onValueChange = { daysOffset = it },
                                label = { Text("Days") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = hoursOffset,
                                onValueChange = { hoursOffset = it },
                                label = { Text("Hours") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (isEditing) {
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    db.schedulerTaskDao().delete(taskToEdit!!)
                                }
                                taskToEdit = null
                            }) {
                                Text("Delete", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        
                        TextButton(onClick = { 
                            showAddDialog = false
                            taskToEdit = null
                        }) {
                            Text("Cancel")
                        }
                        
                        Button(onClick = {
                            val timeOffset = ((daysOffset.toLongOrNull() ?: 0) * 24 * 60 * 60 * 1000) +
                                            ((hoursOffset.toLongOrNull() ?: 0) * 60 * 60 * 1000)
                            val targetTime = if (isEditing) taskToEdit!!.timeMillis else System.currentTimeMillis() + timeOffset
                            
                            val task = SchedulerTask(
                                id = taskToEdit?.id ?: 0,
                                title = title.ifEmpty { "Untitled" },
                                note = note,
                                timeMillis = targetTime
                            )
                            
                            scope.launch(Dispatchers.IO) {
                                if (isEditing) {
                                    db.schedulerTaskDao().update(task)
                                } else {
                                    db.schedulerTaskDao().insert(task)
                                }
                            }
                            
                            showAddDialog = false
                            taskToEdit = null
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
