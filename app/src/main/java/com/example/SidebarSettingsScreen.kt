package com.example

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.utils.PageManager
import com.example.utils.SidebarPage
import java.util.UUID

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SidebarSettingsScreen(onBack: () -> Unit) {
    val configuration = LocalConfiguration.current
    val maxScreenWidth = configuration.screenWidthDp.toFloat()
    val maxScreenHeight = configuration.screenHeightDp.toFloat()

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FloatingReaderPrefs", Context.MODE_PRIVATE) }
    
    var customisingPage by remember { mutableStateOf<SidebarPage?>(null) }
    var selectedActionPage by remember { mutableStateOf<SidebarPage?>(null) }
    var pageActionIndex by remember { mutableStateOf(-1) }

    if (customisingPage != null) {
        PageCustomizeScreen(
            page = customisingPage!!,
            onSave = { updated ->
                val newPages = PageManager.getPages(prefs).toMutableList()
                val idx = newPages.indexOfFirst { it.id == updated.id }
                if (idx != -1) {
                    newPages[idx] = updated
                    PageManager.savePages(prefs, newPages)
                }
            },
            onBack = {
                customisingPage = null
            }
        )
        return
    }

    // Sidebar options
    var sidebarColumns by remember { mutableStateOf(prefs.getInt("sidebar_columns", 4)) }
    var sidebarWidth by remember { mutableStateOf(prefs.getInt("sidebar_width", 320)) }
    var sidebarHeight by remember { mutableStateOf(prefs.getInt("sidebar_height", 450)) }
    var sidebarWrapContent by remember { mutableStateOf(prefs.getBoolean("sidebar_wrap_content", true)) }
    var sidebarColorHex by remember { mutableStateOf(prefs.getString("sidebar_color", "#000000") ?: "#000000") }
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
                            valueRange = 200f..maxScreenWidth,
                                steps = ((maxScreenWidth - 200f) / 10f).toInt()
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
                            valueRange = 300f..maxScreenHeight,
                                steps = ((maxScreenHeight - 300f) / 10f).toInt()
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
                    headlineContent = { Text("Sidebar Color") },
                    supportingContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val presetColors = listOf(
                                "#000000", "#FFFFFF", "#FF5252", "#4CAF50", "#2196F3", "#FFEB3B", "#87CEEB"
                            )
                            presetColors.forEach { colorString ->
                                val parsedColor = try {
                                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(colorString))
                                } catch (e: Exception) {
                                    androidx.compose.ui.graphics.Color.Gray
                                }
                                val baseColorStr = if (colorString.length >= 7) colorString.substring(colorString.length - 6) else colorString
                                val currentBaseStr = if (sidebarColorHex.length >= 7) sidebarColorHex.substring(sidebarColorHex.length - 6) else sidebarColorHex
                                val isSelected = baseColorStr.equals(currentBaseStr, ignoreCase = true)
                                
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(parsedColor, androidx.compose.foundation.shape.CircleShape)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Gray,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable {
                                            sidebarColorHex = colorString
                                            prefs.edit().putString("sidebar_color", colorString).apply()
                                        }
                                )
                            }
                        }
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
                Box {
                    ListItem(
                        modifier = Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = {
                                if (index > 0 && pages.size > 1) { // don't allow editing/removing default Apps Grid
                                    selectedActionPage = page
                                    pageActionIndex = index
                                }
                            }
                        ),
                        headlineContent = { Text(page.title) },
                        supportingContent = { Text(page.type.replace("_", " ").capitalize()) },
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
                            }
                        }
                    )
                    
                    DropdownMenu(
                        expanded = selectedActionPage == page && pageActionIndex == index,
                        onDismissRequest = {
                            selectedActionPage = null
                            pageActionIndex = -1
                        }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit / Customize") },
                            onClick = {
                                customisingPage = selectedActionPage
                                selectedActionPage = null
                                pageActionIndex = -1
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                val newPages = pages.toMutableList()
                                newPages.removeAt(pageActionIndex)
                                if (defaultIndex == pageActionIndex) defaultIndex = 0
                                else if (defaultIndex > pageActionIndex) defaultIndex--
                                pages = newPages
                                savePages()
                                
                                selectedActionPage = null
                                pageActionIndex = -1
                            }
                        )
                    }
                }
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
                            "notifications" to "Notifications",
                            "reader" to "Reader"
                        )
                        types.forEach { (type, title) ->
                            TextButton(onClick = {
                                val newPages = pages.toMutableList()
                                val newPage = SidebarPage(id = UUID.randomUUID().toString(), type = type, title = title)
                                if (type == "calculator") {
                                    newPage.useCustomSettings = true
                                    newPage.width = 340
                                    newPage.height = 480
                                } else if (type == "scheduler") {
                                    newPage.useCustomSettings = true
                                    newPage.width = 360
                                    newPage.height = 500
                                } else if (type == "compass") {
                                    newPage.useCustomSettings = true
                                    newPage.width = 300
                                    newPage.height = 350
                                } else if (type == "reader") {
                                    newPage.useCustomSettings = true
                                    newPage.width = 380
                                    newPage.height = 600
                                }
                                newPages.add(newPage)
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
