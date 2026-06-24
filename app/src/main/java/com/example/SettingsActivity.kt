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
                    onBack = onFinish
                )
                "reader" -> ReaderSettingsScreen(
                    onBack = { if (startRoute == "reader") onFinish() else currentRoute = "main" }
                )
                "general" -> GeneralSettingsScreen(
                    onBack = { currentRoute = "main" }
                )
                "netspeed" -> NetSpeedSettingsScreen(
                    onBack = { currentRoute = "main" }
                )
                "data" -> DataSettingsScreen(
                    onBack = { currentRoute = "main" }
                )
                "pages" -> PageManagementSettingsScreen(
                    onBack = { currentRoute = "main" }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen(onNavigateToReader: () -> Unit, onNavigateToGeneral: () -> Unit, onNavigateToNetSpeed: () -> Unit, onNavigateToData: () -> Unit, onNavigateToPages: () -> Unit, onBack: () -> Unit) {
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
                    headlineContent = { Text("General Settings") },
                    supportingContent = { Text("Sidebar, handle customization") },
                    modifier = Modifier.clickable { onNavigateToGeneral() }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Sidebar Page Management") },
                    supportingContent = { Text("Add, remove, reorder sidebar pages") },
                    modifier = Modifier.clickable { onNavigateToPages() }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("NetSpeed Indicator Settings") },
                    supportingContent = { Text("Toggle, units, and data usage statistics") },
                    modifier = Modifier.clickable { onNavigateToNetSpeed() }
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
                    Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FloatingReaderPrefs", Context.MODE_PRIVATE) }
    
    // Handle options
    var handleColor by remember { mutableStateOf(prefs.getString("handle_color", "Default") ?: "Default") }
    var handleShape by remember { mutableStateOf(prefs.getString("handle_shape", "Default") ?: "Default") }
    
    // Sidebar options
    var sidebarColumns by remember { mutableStateOf(prefs.getInt("sidebar_columns", 4)) }
    var sidebarRows by remember { mutableStateOf(prefs.getInt("sidebar_rows", 5)) }
    var sidebarStickMode by remember { mutableStateOf(prefs.getString("sidebar_stick_mode", "Edge") ?: "Edge") }
    var sidebarLengthMode by remember { mutableStateOf(prefs.getString("sidebar_length_mode", "Wrap") ?: "Wrap") }
    var sidebarTransparency by remember { mutableStateOf(prefs.getFloat("sidebar_transparency", 0.9f)) }

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
                Text(
                    text = "Handle Customization",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Color") },
                    supportingContent = { Text(handleColor) },
                    modifier = Modifier.clickable { 
                        // Placeholder
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Shape") },
                    supportingContent = { Text(handleShape) },
                    modifier = Modifier.clickable { 
                        // Placeholder
                    }
                )
                Divider()
                
                Text(
                    text = "Sidebar Customization",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Columns") },
                    supportingContent = {
                        Slider(
                            value = sidebarColumns.toFloat(),
                            onValueChange = { 
                                sidebarColumns = it.toInt()
                                prefs.edit().putInt("sidebar_columns", it.toInt()).apply()
                            },
                            valueRange = 2f..6f,
                            steps = 3
                        )
                    },
                    trailingContent = { Text(sidebarColumns.toString()) }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Rows") },
                    supportingContent = {
                        Slider(
                            value = sidebarRows.toFloat(),
                            onValueChange = { 
                                sidebarRows = it.toInt()
                                prefs.edit().putInt("sidebar_rows", it.toInt()).apply()
                            },
                            valueRange = 2f..8f,
                            steps = 5
                        )
                    },
                    trailingContent = { Text(sidebarRows.toString()) }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Stick Mode") },
                    supportingContent = { Text(sidebarStickMode) },
                    modifier = Modifier.clickable { 
                        // Placeholder
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Length Mode") },
                    supportingContent = { Text(sidebarLengthMode) },
                    modifier = Modifier.clickable { 
                        // Placeholder
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Background Transparency") },
                    supportingContent = {
                        Slider(
                            value = sidebarTransparency,
                            onValueChange = { 
                                sidebarTransparency = it
                                prefs.edit().putFloat("sidebar_transparency", it).apply()
                            },
                            valueRange = 0f..1f,
                            steps = 10
                        )
                    }
                )
                Divider()
                
                Text(
                    text = "Pages",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Sidebar Pages (Placeholder)") },
                    supportingContent = { Text("Configure multiple pages") },
                    modifier = Modifier.clickable { 
                        // Placeholder
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
                    Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
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
