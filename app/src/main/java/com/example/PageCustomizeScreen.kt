package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.utils.SidebarPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageCustomizeScreen(
    page: SidebarPage,
    onSave: (SidebarPage) -> Unit,
    onBack: () -> Unit
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxScreenWidth = configuration.screenWidthDp.toFloat()
    val maxScreenHeight = configuration.screenHeightDp.toFloat()
    
    var useCustomSettings by remember { mutableStateOf(page.useCustomSettings) }
    var width by remember { mutableStateOf(page.width) }
    var height by remember { mutableStateOf(page.height) }
    var wrapContentHeight by remember { mutableStateOf(page.wrapContentHeight) }
    var transparency by remember { mutableStateOf(page.transparency) }
    var title by remember { mutableStateOf(page.title) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Customize: ${page.title}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {
                val updatedPage = page.copy(
                    title = title,
                    useCustomSettings = useCustomSettings,
                    width = width,
                    height = height,
                    wrapContentHeight = wrapContentHeight,
                    transparency = transparency
                )
                onSave(updatedPage)
                onBack()
            }) {
                Text("Save")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Page Title") },
                    supportingContent = {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                )
                Divider()

                ListItem(
                    headlineContent = { Text("Use Custom Settings") },
                    supportingContent = { Text("Override global sidebar settings for this page") },
                    trailingContent = {
                        Switch(
                            checked = useCustomSettings,
                            onCheckedChange = { useCustomSettings = it }
                        )
                    }
                )
                Divider()

                if (useCustomSettings) {
                    ListItem(
                        headlineContent = { Text("Width") },
                        supportingContent = {
                            Slider(
                                value = width.toFloat(),
                                onValueChange = { width = it.toInt() },
                                valueRange = 200f..maxScreenWidth,
                                steps = ((maxScreenWidth - 200f) / 10f).toInt()
                            )
                        },
                        trailingContent = { Text("${width}dp") }
                    )
                    Divider()
                    
                    ListItem(
                        headlineContent = { Text("Height (Max)") },
                        supportingContent = {
                            Slider(
                                value = height.toFloat(),
                                onValueChange = { height = it.toInt() },
                                valueRange = 300f..maxScreenHeight,
                                steps = ((maxScreenHeight - 300f) / 10f).toInt()
                            )
                        },
                        trailingContent = { Text("${height}dp") }
                    )
                    Divider()
                    
                    ListItem(
                        headlineContent = { Text("Wrap Content Height") },
                        supportingContent = { Text("Shrink to fit content instead of fixed height") },
                        trailingContent = {
                            Switch(
                                checked = wrapContentHeight,
                                onCheckedChange = { wrapContentHeight = it }
                            )
                        }
                    )
                    Divider()
                    
                    ListItem(
                        headlineContent = { Text("Background Opacity") },
                        supportingContent = {
                            Slider(
                                value = transparency,
                                onValueChange = { transparency = it },
                                valueRange = 0f..1f,
                                steps = 20
                            )
                        },
                        trailingContent = { Text("${(transparency * 100).toInt()}%") }
                    )
                    Divider()
                }
                
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
