package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.db.AppDatabase
import com.example.db.NotificationHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Intent
import androidx.core.content.FileProvider

class NotificationHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NotificationHistoryScreen(
                    onBack = { finish() },
                    onExport = { exportHistory(it) }
                )
            }
        }
    }

    private fun exportHistory(history: List<NotificationHistory>) {
        val file = File(cacheDir, "notification_history.txt")
        val writer = FileWriter(file)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        history.forEach { item ->
            val time = format.format(Date(item.timestamp))
            writer.write("[$time] ${item.appName} (${item.packageName})\n")
            writer.write("Title: ${item.title}\n")
            writer.write("Text: ${item.text}\n")
            writer.write("------------------------\n\n")
        }
        writer.close()
        
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export History"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(onBack: () -> Unit, onExport: (List<NotificationHistory>) -> Unit) {
    LaunchedEffect(Unit) {
        com.example.LogKeeper.writeLog("History", "Notification history viewed")
    }

    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).notificationHistoryDao() }
    val scope = rememberCoroutineScope()
    
    var history by remember { mutableStateOf<List<NotificationHistory>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    val prefs = remember { context.getSharedPreferences("NotificationPrefs", Context.MODE_PRIVATE) }
    var hiddenPackages by remember { 
        mutableStateOf(prefs.getStringSet("history_hidden_packages", prefs.getStringSet("hidden_packages", emptySet())) ?: emptySet())
    }
    
    var showFilterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, hiddenPackages) {
        if (searchQuery.isBlank()) {
            dao.getFiltered(hiddenPackages.toList()).collectLatest { history = it }
        } else {
            dao.search(searchQuery, hiddenPackages.toList()).collectLatest { history = it }
        }
    }

    Scaffold(
        topBar = {
            if (isSearching) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearching = false
                            searchQuery = ""
                        }) {
                            Icon(Icons.Default.ArrowBack, "Close Search")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Notification History") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(Icons.Default.FilterList, "Filter")
                        }
                        IconButton(onClick = { onExport(history) }) {
                            Icon(Icons.Default.Share, "Export")
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                dao.deleteAll()
                            }
                        }) {
                            Icon(Icons.Default.Delete, "Clear All")
                        }
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(history, key = { it.id }) { item ->
                HistoryItem(item)
            }
        }
        
        if (showFilterDialog) {
            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text("Filter Apps in History") },
                text = {
                    val pm = context.packageManager
                    // Get a list of all packages we have in history to filter
                    val appsInHistory = history.map { it.packageName to it.appName }.distinctBy { it.first }
                    
                    LazyColumn {
                        items(appsInHistory) { (pkg, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = !hiddenPackages.contains(pkg),
                                    onCheckedChange = { checked ->
                                        val newHidden = hiddenPackages.toMutableSet()
                                        if (checked) {
                                            newHidden.remove(pkg)
                                        } else {
                                            newHidden.add(pkg)
                                        }
                                        hiddenPackages = newHidden
                                        prefs.edit().putStringSet("history_hidden_packages", newHidden).apply()
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(name)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFilterDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryItem(item: NotificationHistory) {
    val format = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val timeStr = format.format(Date(item.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (item.title.isNotEmpty()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            if (item.text.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
            }
        }
    }
}
