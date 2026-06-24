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
fun PageManagementSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FloatingReaderPrefs", Context.MODE_PRIVATE) }
    
    var pages by remember { mutableStateOf(PageManager.getPages(prefs)) }
    var defaultIndex by remember { mutableStateOf(PageManager.getDefaultPageIndex(prefs)) }
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    fun savePages() {
        PageManager.savePages(prefs, pages)
        PageManager.saveDefaultPageIndex(prefs, defaultIndex)
        // Notify service to reload pages if needed? We can just send a broadcast or let service reload on next show.
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Page Management") },
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
            itemsIndexed(pages) { index, page ->
                ListItem(
                    headlineContent = { Text(page.title) },
                    supportingContent = { Text(page.type.capitalize()) },
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
                                if (index > 1) { // 1 instead of 0 to protect index 0
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
                                Icon(Icons.Default.ArrowUpward, "Move Up")
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
                                Icon(Icons.Default.ArrowDownward, "Move Down")
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
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    }
                )
                Divider()
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
