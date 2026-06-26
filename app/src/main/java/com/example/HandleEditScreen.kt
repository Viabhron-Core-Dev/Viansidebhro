package com.example

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandleEditScreen(handleId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("FloatingReaderPrefs", Context.MODE_PRIVATE) }
    
    val prefix = "handle_${handleId}_"
    
    var yPos by remember { mutableFloatStateOf(prefs.getInt("${prefix}y", 500).toFloat()) }
    var sizeWidth by remember { mutableFloatStateOf(prefs.getInt("${prefix}width", if (handleId == "reader") 16 else 6).toFloat()) }
    var sizeHeight by remember { mutableFloatStateOf(prefs.getInt("${prefix}height", if (handleId == "reader") 60 else 120).toFloat()) }
    var colorHex by remember { mutableStateOf(prefs.getString("${prefix}color", if (handleId == "reader") "#44102d42" else "#3318304A") ?: "#3318304A") }
    var shape by remember { mutableStateOf(prefs.getString("${prefix}shape", if (handleId == "reader") "half_oval" else "triangle") ?: "triangle") }
    
    // Gestures state
    val gestureKeys = listOf("tap", "double_tap", "long_press", "swipe_up", "swipe_down", "swipe_left", "swipe_right")
    val gesturesMap = remember { mutableStateMapOf<String, String>() }
    
    DisposableEffect(Unit) {
        prefs.edit().putBoolean("is_handle_edit_mode", true).apply()
        
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key != null && key.startsWith(prefix) && gestureKeys.any { key == "$prefix$it" }) {
                val gesture = key.removePrefix(prefix)
                val action = sharedPreferences.getString(key, "none") ?: "none"
                if (action == "none") {
                    gesturesMap.remove(gesture)
                } else {
                    gesturesMap[gesture] = action
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        
        onDispose {
            prefs.edit().putBoolean("is_handle_edit_mode", false).apply()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    LaunchedEffect(Unit) {
        gestureKeys.forEach { key ->
            val action = prefs.getString("${prefix}$key", "none") ?: "none"
            if (action != "none") {
                gesturesMap[key] = action
            }
        }
    }

    fun updateGesture(gesture: String, action: String) {
        if (action == "none") {
            gesturesMap.remove(gesture)
        } else {
            gesturesMap[gesture] = action
        }
        prefs.edit().putString("${prefix}$gesture", action).apply()
    }
    
    val pageConfigs = com.example.utils.PageManager.getPages(prefs)
    
    val gestureLabels = mapOf(
        "tap" to "Single Tap",
        "double_tap" to "Double Tap",
        "long_press" to "Long Press",
        "swipe_up" to "Swipe Up",
        "swipe_down" to "Swipe Down",
        "swipe_left" to "Swipe Left",
        "swipe_right" to "Swipe Right"
    )
    
    var showAddGestureDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit ${if (handleId == "sidebar") "Sidebar" else "Reader"} Handle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Appearance (Applies Instantly)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Y Position: ${yPos.toInt()}")
            Slider(value = yPos, onValueChange = { 
                yPos = it
                prefs.edit().putInt("${prefix}y", it.toInt()).apply() 
            }, valueRange = 0f..2500f)
            
            Text("Width (Thickness): ${sizeWidth.toInt()}dp")
            Slider(value = sizeWidth, onValueChange = { 
                sizeWidth = it
                prefs.edit().putInt("${prefix}width", it.toInt()).apply()
            }, valueRange = 2f..50f)
            
            Text("Height (Length): ${sizeHeight.toInt()}dp")
            Slider(value = sizeHeight, onValueChange = { 
                sizeHeight = it
                prefs.edit().putInt("${prefix}height", it.toInt()).apply()
            }, valueRange = 20f..300f)
            
            OutlinedTextField(
                value = colorHex,
                onValueChange = { 
                    colorHex = it
                    prefs.edit().putString("${prefix}color", it).apply()
                },
                label = { Text("Color (AARRGGBB hex)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("Shape:")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("triangle", "rectangle", "half_oval", "rounded_rect").forEach { s ->
                    FilterChip(
                        selected = shape == s,
                        onClick = { 
                            shape = s
                            prefs.edit().putString("${prefix}shape", s).apply()
                        },
                        label = { Text(s.replace("_", " ").capitalize()) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Assigned Gestures", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (gesturesMap.isEmpty()) {
                Text("No gestures assigned.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            } else {
                gesturesMap.forEach { (gesture, action) ->
                    val actionName = when {
                        action == "toggle_sidebar" -> "Default Sidebar Page"
                        action == "toggle_reader" -> "Toggle Reader"
                        action.startsWith("open_page:") -> {
                            val type = action.removePrefix("open_page:")
                            "Page: " + (pageConfigs.find { it.type == type }?.title ?: type)
                        }
                        action.startsWith("open_element:") -> {
                            val el = action.removePrefix("open_element:")
                            "Element: " + el
                        }
                        else -> action
                    }
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(gestureLabels[gesture] ?: gesture, style = MaterialTheme.typography.titleSmall)
                                Text(actionName, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { updateGesture(gesture, "none") }) {
                                Icon(Icons.Default.Delete, "Remove")
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { showAddGestureDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, "Add Gesture")
                Spacer(modifier = Modifier.width(8.dp))
                Text("ADD GESTURE")
            }
        }
    }
    
    if (showAddGestureDialog) {
        var selectedGesture by remember { mutableStateOf(gestureKeys.first { !gesturesMap.containsKey(it) } ?: gestureKeys.first()) }
        
        val categoryOptions = listOf(
            "sidebar" to "Default Sidebar Page",
            "page" to "Page",
            "element" to "Open Element"
        )
        var selectedCategory by remember { mutableStateOf("sidebar") }
        var selectedPageType by remember { mutableStateOf(if (pageConfigs.isNotEmpty()) pageConfigs.first().type else "") }
        
        AlertDialog(
            onDismissRequest = { showAddGestureDialog = false },
            title = { Text("Add Gesture") },
            text = {
                Column {
                    ActionDropdown("Select Gesture", selectedGesture, gestureKeys.filter { !gesturesMap.containsKey(it) }.map { it to (gestureLabels[it] ?: it) }) { selectedGesture = it }
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionDropdown("Action Type", selectedCategory, categoryOptions) { selectedCategory = it }
                    
                    if (selectedCategory == "page") {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (pageConfigs.isEmpty()) {
                            Text("No pages available.", color = Color.Red)
                        } else {
                            val pageOptions = pageConfigs.map { it.type to it.title }
                            ActionDropdown("Select Page", selectedPageType, pageOptions) { selectedPageType = it }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedCategory == "sidebar") {
                        updateGesture(selectedGesture, "toggle_sidebar")
                        showAddGestureDialog = false
                    } else if (selectedCategory == "page") {
                        if (selectedPageType.isNotEmpty()) {
                            updateGesture(selectedGesture, "open_page:$selectedPageType")
                            showAddGestureDialog = false
                        }
                    } else if (selectedCategory == "element") {
                        val intent = android.content.Intent(context, com.example.service.FloatingReaderService::class.java).apply {
                            action = "SELECT_ELEMENT_FOR_HANDLE"
                            putExtra("handle_prefix", prefix)
                            putExtra("gesture", selectedGesture)
                        }
                        context.startService(intent)
                        showAddGestureDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGestureDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionDropdown(label: String, selected: String, actions: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = actions.find { it.first == selected }?.second ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(matchTextFieldWidth = true)
        ) {
            actions.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                )
            }
        }
    }
}

