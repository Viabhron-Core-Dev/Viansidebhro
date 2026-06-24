package com.example

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    
    // Gestures
    var swipeUp by remember { mutableStateOf(prefs.getString("${prefix}swipe_up", "none") ?: "none") }
    var swipeDown by remember { mutableStateOf(prefs.getString("${prefix}swipe_down", "none") ?: "none") }
    var swipeLeft by remember { mutableStateOf(prefs.getString("${prefix}swipe_left", "none") ?: "none") }
    var swipeRight by remember { mutableStateOf(prefs.getString("${prefix}swipe_right", "none") ?: "none") }
    var doubleTap by remember { mutableStateOf(prefs.getString("${prefix}double_tap", "none") ?: "none") }
    var longPress by remember { mutableStateOf(prefs.getString("${prefix}long_press", "none") ?: "none") }

    fun save() {
        prefs.edit()
            .putInt("${prefix}y", yPos.toInt())
            .putInt("${prefix}width", sizeWidth.toInt())
            .putInt("${prefix}height", sizeHeight.toInt())
            .putString("${prefix}color", colorHex)
            .putString("${prefix}shape", shape)
            .putString("${prefix}swipe_up", swipeUp)
            .putString("${prefix}swipe_down", swipeDown)
            .putString("${prefix}swipe_left", swipeLeft)
            .putString("${prefix}swipe_right", swipeRight)
            .putString("${prefix}double_tap", doubleTap)
            .putString("${prefix}long_press", longPress)
            .apply()
    }
    
    val actions = listOf(
        "none" to "None",
        "open_apps" to "Open Apps Page",
        "open_scheduler" to "Open Scheduler",
        "open_calculator" to "Open Calculator",
        "open_compass" to "Open Compass",
        "action_home" to "System: Home",
        "action_back" to "System: Back",
        "action_recents" to "System: Recents",
        "action_notifications" to "System: Notifications",
        "action_quick_settings" to "System: Quick Settings",
        "action_power_dialog" to "System: Power Dialog",
        "action_lock_screen" to "System: Lock Screen",
        "action_screenshot" to "System: Screenshot"
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit ${if (handleId == "sidebar") "Sidebar" else "Reader"} Handle") },
                navigationIcon = {
                    IconButton(onClick = {
                        save()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Y Position: ${yPos.toInt()}")
            Slider(value = yPos, onValueChange = { yPos = it }, valueRange = 0f..2500f)
            
            Text("Width (Thickness): ${sizeWidth.toInt()}dp")
            Slider(value = sizeWidth, onValueChange = { sizeWidth = it }, valueRange = 2f..50f)
            
            Text("Height (Length): ${sizeHeight.toInt()}dp")
            Slider(value = sizeHeight, onValueChange = { sizeHeight = it }, valueRange = 20f..300f)
            
            OutlinedTextField(
                value = colorHex,
                onValueChange = { colorHex = it },
                label = { Text("Color (AARRGGBB hex)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("Shape:")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("triangle", "rectangle", "half_oval", "rounded_rect").forEach { s ->
                    FilterChip(
                        selected = shape == s,
                        onClick = { shape = s },
                        label = { Text(s.replace("_", " ").capitalize()) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Gestures", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            ActionDropdown("Swipe Up", swipeUp, actions) { swipeUp = it }
            ActionDropdown("Swipe Down", swipeDown, actions) { swipeDown = it }
            ActionDropdown("Swipe Left", swipeLeft, actions) { swipeLeft = it }
            ActionDropdown("Swipe Right", swipeRight, actions) { swipeRight = it }
            ActionDropdown("Double Tap", doubleTap, actions) { doubleTap = it }
            ActionDropdown("Long Press", longPress, actions) { longPress = it }
        }
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
            onDismissRequest = { expanded = false }
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
