package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.utils.BackupHelper
import com.example.util.AppLogger
import android.widget.Toast
import android.content.Intent

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startRoute = intent.getStringExtra("start_route") ?: "main"
        
        setContent {
            MaterialTheme {
                SettingsApp(startRoute = startRoute) {
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsApp(startRoute: String, onFinish: () -> Unit) {
    var currentRoute by remember { mutableStateOf(startRoute) }
    
    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentRoute) {
                "main" -> MainSettingsScreen(
                    onNavigateToReader = { currentRoute = "reader" },
                    onNavigateToGeneral = { currentRoute = "general" },
                    onBack = onFinish
                )
                "reader" -> ReaderSettingsScreen(
                    onBack = { if (startRoute == "reader") onFinish() else currentRoute = "main" }
                )
                "general" -> GeneralSettingsScreen(
                    onBack = { currentRoute = "main" }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(onNavigateToReader: () -> Unit, onNavigateToGeneral: () -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("eBook Reader Settings") },
                    supportingContent = { Text("Theme, font size, gestures") },
                    modifier = Modifier.clickable { onNavigateToReader() }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("General Settings") },
                    supportingContent = { Text("Backup, restore, logging") },
                    modifier = Modifier.clickable { onNavigateToGeneral() }
                )
                Divider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FloatingReaderPrefs", Context.MODE_PRIVATE) }
    
    var tapToTurn by remember { mutableStateOf(prefs.getBoolean("tap_to_turn", true)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keep_screen_on", true)) }
    var enableBookmarks by remember { mutableStateOf(prefs.getBoolean("enable_bookmarks", false)) }
    var continuousSave by remember { mutableStateOf(prefs.getBoolean("continuous_save", false)) }
    var useScopedDir by remember { mutableStateOf(prefs.getBoolean("use_scoped_dir", false)) }
    var fontScale by remember { mutableStateOf(prefs.getFloat("font_size_scale", 1.0f)) }
    var useDarkTheme by remember { mutableStateOf(prefs.getBoolean("use_dark_theme", true)) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("eBook Reader Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Dark Theme") },
                    trailingContent = {
                        Switch(
                            checked = useDarkTheme,
                            onCheckedChange = { 
                                useDarkTheme = it
                                prefs.edit().putBoolean("use_dark_theme", it).apply()
                            }
                        )
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Font Size Scale") },
                    supportingContent = {
                        Slider(
                            value = fontScale,
                            onValueChange = { 
                                fontScale = it
                                prefs.edit().putFloat("font_size_scale", it).apply()
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 15
                        )
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Tap to Turn") },
                    supportingContent = { Text("Tap edges of screen to turn pages") },
                    trailingContent = {
                        Switch(
                            checked = tapToTurn,
                            onCheckedChange = { 
                                tapToTurn = it
                                prefs.edit().putBoolean("tap_to_turn", it).apply()
                            }
                        )
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Keep Screen On") },
                    supportingContent = { Text("Prevent screen from turning off while reading") },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { 
                                keepScreenOn = it
                                prefs.edit().putBoolean("keep_screen_on", it).apply()
                            }
                        )
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Enable Bookmarks") },
                    trailingContent = {
                        Switch(
                            checked = enableBookmarks,
                            onCheckedChange = { 
                                enableBookmarks = it
                                prefs.edit().putBoolean("enable_bookmarks", it).apply()
                                Toast.makeText(context, "Restart reader to apply", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Continuous Save") },
                    supportingContent = { Text("Save progress constantly (may impact performance)") },
                    trailingContent = {
                        Switch(
                            checked = continuousSave,
                            onCheckedChange = { 
                                continuousSave = it
                                prefs.edit().putBoolean("continuous_save", it).apply()
                            }
                        )
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Use Scoped Directory") },
                    trailingContent = {
                        Switch(
                            checked = useScopedDir,
                            onCheckedChange = { 
                                useScopedDir = it
                                prefs.edit().putBoolean("use_scoped_dir", it).apply()
                            }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("General Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Backup Data (No Books)") },
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Backing up data...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val res = BackupHelper.backupData(context, false)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Backup saved: ${java.io.File(res.getOrNull() ?: "").name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Backup Data (Full)") },
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Backing up full data...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val res = BackupHelper.backupData(context, true)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Backup saved: ${java.io.File(res.getOrNull() ?: "").name}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Export Logs") },
                    modifier = Modifier.clickable {
                        try {
                            val f = AppLogger.export(context)
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", f)
                            val i = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(i, "Export Logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                            AppLogger.export(context)
                        }
                    }
                )
            }
        }
    }
}
