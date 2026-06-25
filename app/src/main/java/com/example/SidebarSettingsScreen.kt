package com.example

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.utils.PageManager
import com.example.utils.SidebarPage
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidebarSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FloatingReaderPrefs", Context.MODE_PRIVATE) }
    
    // Sidebar options
    var sidebarColumns by remember { mutableStateOf(prefs.getInt("sidebar_columns", 4)) }
    var sidebarWidth by remember { mutableStateOf(prefs.getInt("sidebar_width", 320)) }
    var sidebarHeight by remember { mutableStateOf(prefs.getInt("sidebar_height", 450)) }
    var sidebarWrapContent by remember { mutableStateOf(prefs.getBoolean("sidebar_wrap_content", true)) }
    var sidebarTransparency by remember { mutableStateOf(prefs.getFloat("sidebar_transparency", 0.9f)) }
    var sidebarPositionLeft by remember { mutableStateOf(prefs.getBoolean("sidebar_position_left", false)) }

    // Pages
    var pages by remember { mutableStateOf(PageManager.getPages(prefs)) }
    var defaultIndex by remember { mutableStateOf(PageManager.getDefaultPageIndex(prefs)) }
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    fun savePages() {
        PageManager.savePages(prefs, pages)
        PageManager.saveDefaultPageIndex(prefs, defaultIndex)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sidebar Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        savePages()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add Page")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    text = "Appearance & Layout",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Width") },
                    supportingContent = {
                        Slider(
                            value = sidebarWidth.toFloat(),
                            onValueChange = { 
                                sidebarWidth = it.toInt()
                                prefs.edit().putInt("sidebar_width", it.toInt()).apply()
                            },
                            valueRange = 200f..800f,
                            steps = 60
                        )
                    },
                    trailingContent = { Text("${sidebarWidth}dp") }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Height (Max)") },
                    supportingContent = {
                        Slider(
                            value = sidebarHeight.toFloat(),
                            onValueChange = { 
                                sidebarHeight = it.toInt()
                                prefs.edit().putInt("sidebar_height", it.toInt()).apply()
                            },
                            valueRange = 300f..1000f,
                            steps = 70
                        )
                    },
                    trailingContent = { Text("${sidebarHeight}dp") }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Wrap Content Height") },
                    supportingContent = { Text("Shrink to fit content instead of fixed height") },
                    trailingContent = {
                        Switch(
                            checked = sidebarWrapContent,
                            onCheckedChange = { 
                                sidebarWrapContent = it
                                prefs.edit().putBoolean("sidebar_wrap_content", it).apply()
                            }
                        )
                    }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Background Opacity") },
                    supportingContent = {
                        Slider(
                            value = sidebarTransparency,
                            onValueChange = { 
                                sidebarTransparency = it
                                prefs.edit().putFloat("sidebar_transparency", it).apply()
                            },
                            valueRange = 0f..1f,
                            steps = 20
                        )
                    },
                    trailingContent = { Text("${(sidebarTransparency * 100).toInt()}%") }
                )
                Divider()
                ListItem(
                    headlineContent = { Text("Columns (Apps Grid)") },
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
                    headlineContent = { Text("Sidebar Position") },
                    supportingContent = { Text(if (sidebarPositionLeft) "Left Side" else "Right Side") },
                    trailingContent = {
                        Switch(
                            checked = sidebarPositionLeft,
                            onCheckedChange = { 
                                sidebarPositionLeft = it
                                prefs.edit().putBoolean("sidebar_position_left", it).apply()
                            }
                        )
                    }
                )
                Divider()
                
                Text(
                    text = "Pages Management",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
            }
            
            itemsIndexed(pages) { index, page ->
                ListItem(
                    headlineContent = { Text(page.title) },
                    supportingContent = { Text(page.type.replace("_", " ").capitalize()) },
                    leadingContent = {
                        RadioButton(
                            selected = index == defaultIndex,
                            onClick = {
                                defaultIndex = index
                                savePages()
                            }
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = {
                                if (index > 1) { // 1 instead of 0 to protect index 0 apps grid
                                    val newPages = pages.toMutableList()
                                    val temp = newPages[index]
                                    newPages[index] = newPages[index - 1]
                                    newPages[index - 1] = temp
                                    if (defaultIndex == index) defaultIndex = index - 1
                                    else if (defaultIndex == index - 1) defaultIndex = index
                                    pages = newPages
                                    savePages()
                                }
                            }, enabled = index > 1) {
                                Icon(Icons.Default.ArrowUpward, "Up")
                            }
                            IconButton(onClick = {
                                if (index > 0 && index < pages.size - 1) {
                                    val newPages = pages.toMutableList()
                                    val temp = newPages[index]
                                    newPages[index] = newPages[index + 1]
                                    newPages[index + 1] = temp
                                    if (defaultIndex == index) defaultIndex = index + 1
                                    else if (defaultIndex == index + 1) defaultIndex = index
                                    pages = newPages
                                    savePages()
                                }
                            }, enabled = index > 0 && index < pages.size - 1) {
                                Icon(Icons.Default.ArrowDownward, "Down")
                            }
                            IconButton(onClick = {
                                if (index > 0 && pages.size > 1) {
                                    val newPages = pages.toMutableList()
                                    newPages.removeAt(index)
                                    if (defaultIndex == index) defaultIndex = 0
                                    else if (defaultIndex > index) defaultIndex--
                                    pages = newPages
                                    savePages()
                                }
                            }, enabled = index > 0 && pages.size > 1) {
                                Icon(Icons.Default.Delete, "Del")
                            }
                        }
                    }
                )
                Divider()
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
            }
        }
        
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Sidebar Page") },
                text = {
                    Column {
                        val types = listOf(
                            "apps" to "Apps Grid",
                            "system_actions" to "System Actions",
                            "contacts" to "Contacts",
                            "scheduler" to "Scheduler",
                            "calculator" to "Calculator",
                            "compass" to "Compass",
                            "reader" to "Reader"
                        )
                        types.forEach { (type, title) ->
                            TextButton(onClick = {
                                val newPages = pages.toMutableList()
                                newPages.add(SidebarPage(id = UUID.randomUUID().toString(), type = type, title = title))
                                pages = newPages
                                savePages()
                                showAddDialog = false
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(title, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
