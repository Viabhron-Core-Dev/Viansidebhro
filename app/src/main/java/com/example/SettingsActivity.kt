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
import android.os.Build
import android.net.Uri

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
    
    androidx.activity.compose.BackHandler {
        if (currentRoute == "main" || currentRoute == startRoute) {
            onFinish()
        } else {
            currentRoute = "main"
        }
    }
    
    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentRoute) {
                "main" -> MainSettingsScreen(
                    onNavigateToReader = { currentRoute = "reader" },
                    onNavigateToGeneral = { currentRoute = "general" },
                    onNavigateToNetSpeed = { currentRoute = "netspeed" },
                    onNavigateToData = { currentRoute = "data" },
                    onNavigateToPages = { currentRoute = "pages" },
                    onNavigateToHandles = { currentRoute = "handles" },
                    onNavigateToCallRecorder = { currentRoute = "call_recorder" },
                    onBack = onFinish
                )
                "reader" -> ReaderSettingsScreen(
                    onBack = { if (startRoute == "reader") onFinish() else currentRoute = "main" }
                )
                "general" -> SidebarSettingsScreen(
                    onBack = { currentRoute = "main" }
                )
                "netspeed" -> NetSpeedSettingsScreen(
                    onBack = { currentRoute = "main" }
                )
                "data" -> DataSettingsScreen(
                    onBack = { currentRoute = "main" }
                )
                "pages" -> SidebarSettingsScreen(
                    onBack = { currentRoute = "main" }
                )
                "handles" -> HandlesListSettingsScreen(
                    onNavigateToHandle = { currentRoute = "handle_$it" },
                    onBack = { currentRoute = "main" }
                )
                "call_recorder" -> CallRecorderSettingsScreen(
                    onBack = { currentRoute = "main" }
                )
            }
            if (currentRoute.startsWith("handle_")) {
                val handleId = currentRoute.removePrefix("handle_")
                HandleEditScreen(
                    handleId = handleId,
                    onBack = { currentRoute = "handles" }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(onNavigateToReader: () -> Unit, onNavigateToGeneral: () -> Unit, onNavigateToNetSpeed: () -> Unit, onNavigateToData: () -> Unit, onNavigateToPages: () -> Unit, onNavigateToHandles: () -> Unit, onNavigateToCallRecorder: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
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
                    supportingContent = { Text("Theme, font size, gestures, backups, logs") },
                    modifier = Modifier.clickable { onNavigateToReader() }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Sidebar Settings") },
                    supportingContent = { Text("Sidebar appearance and pages management") },
                    modifier = Modifier.clickable { onNavigateToGeneral() }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Handles Settings") },
                    supportingContent = { Text("Customize trigger handles & gestures") },
                    modifier = Modifier.clickable { onNavigateToHandles() }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("NetSpeed Indicator Settings") },
                    supportingContent = { Text("Toggle, units, and data usage statistics") },
                    modifier = Modifier.clickable { onNavigateToNetSpeed() }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Call Recorder Settings") },
                    supportingContent = { Text("Automatic call recording & privacy") },
                    modifier = Modifier.clickable { onNavigateToCallRecorder() }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Log Keeper") },
                    supportingContent = { Text("View system logs") },
                    modifier = Modifier.clickable { 
                        val intent = Intent(context, com.example.LogKeeperActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Data & Backup") },
                    supportingContent = { Text("Full app backup and restore") },
                    modifier = Modifier.clickable { onNavigateToData() }
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
    val coroutineScope = rememberCoroutineScope()
    
    var tapToTurn by remember { mutableStateOf(prefs.getBoolean("tap_to_turn", true)) }
    var keepScreenOn by remember { mutableStateOf(prefs.getBoolean("keep_screen_on", true)) }
    var enableBookmarks by remember { mutableStateOf(prefs.getBoolean("enable_bookmarks", false)) }
    var continuousSave by remember { mutableStateOf(prefs.getBoolean("continuous_save", false)) }
    var useScopedDir by remember { mutableStateOf(prefs.getBoolean("use_scoped_dir", false)) }
    var fontScale by remember { mutableStateOf(prefs.getFloat("font_size_scale", 1.0f)) }
    var useDarkTheme by remember { mutableStateOf(prefs.getBoolean("use_dark_theme", true)) }
    var readerHandleEnabled by remember { mutableStateOf(prefs.getBoolean("reader_handle_enabled", false)) }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Importing data...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                val res = BackupHelper.importData(context, uri)
                if (res.isSuccess) {
                    Toast.makeText(context, "Import successful. Please restart the app.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Import failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val storageLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        val hasPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()
        useScopedDir = hasPerm
        prefs.edit().putBoolean("use_scoped_dir", hasPerm).apply()
    }

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
                    headlineContent = { Text("Reader Floating Handle") },
                    supportingContent = { Text("Show a dedicated handle to quickly open the reader") },
                    trailingContent = {
                        Switch(
                            checked = readerHandleEnabled,
                            onCheckedChange = { 
                                readerHandleEnabled = it
                                prefs.edit().putBoolean("reader_handle_enabled", it).apply()
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
                    headlineContent = { Text("File Explorer Mode") },
                    supportingContent = { Text("Browse all folders for books directly") },
                    trailingContent = {
                        Switch(
                            checked = useScopedDir,
                            onCheckedChange = { checked -> 
                                if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:" + context.packageName)
                                        }
                                        storageLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        storageLauncher.launch(intent)
                                    }
                                } else {
                                    useScopedDir = checked
                                    prefs.edit().putBoolean("use_scoped_dir", checked).apply()
                                }
                            }
                        )
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Backup Reader Data") },
                    supportingContent = { Text("Database and settings, no books") },
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Backing up reader data...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val res = BackupHelper.backupData(context, includeBooks = false, includePrefs = false)
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
                    headlineContent = { Text("Backup Reader Data (With Books)") },
                    supportingContent = { Text("Database, settings, and downloaded books") },
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Backing up reader data with books...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val res = BackupHelper.backupData(context, includeBooks = true, includePrefs = false)
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
                    headlineContent = { Text("Import Reader Backup") },
                    modifier = Modifier.clickable {
                        importLauncher.launch("application/zip")
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



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Importing full app data...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                val res = BackupHelper.importData(context, uri)
                if (res.isSuccess) {
                    Toast.makeText(context, "Import successful. Please restart the app.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Import failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Data & Backup") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Reset Data Tracking") },
                    supportingContent = { Text("Reset today's tracked data usage to 0 MB. Use this if the tracking spikes due to an Android error.") },
                    modifier = Modifier.clickable {
                        context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE).edit()
                            .putLong("daily_mobile_rx", 0L)
                            .putLong("daily_mobile_tx", 0L)
                            .putLong("daily_wifi_rx", 0L)
                            .putLong("daily_wifi_tx", 0L)
                            .apply()
                        Toast.makeText(context, "Data usage reset to 0", Toast.LENGTH_SHORT).show()
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Backup Full App Data") },
                    supportingContent = { Text("Includes everything: reader data, sidebar structure, and all settings.") },
                    modifier = Modifier.clickable {
                        Toast.makeText(context, "Backing up full app data...", Toast.LENGTH_SHORT).show()
                        coroutineScope.launch {
                            val res = BackupHelper.backupData(context, includeBooks = true, includePrefs = true)
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
                    headlineContent = { Text("Import Full App Data") },
                    supportingContent = { Text("Restore a previously backed-up full app data zip.") },
                    modifier = Modifier.clickable {
                        importLauncher.launch("application/zip")
                    }
                )
                Divider()
            }
        }
    }
}
