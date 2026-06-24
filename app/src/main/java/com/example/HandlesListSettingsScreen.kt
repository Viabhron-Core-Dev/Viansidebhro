package com.example

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlesListSettingsScreen(onNavigateToHandle: (String) -> Unit, onBack: () -> Unit) {
    val handles = listOf(
        "sidebar" to "Sidebar Handle",
        "reader" to "Reader Handle"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Handles Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(handles) { (id, title) ->
                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { Text("Configure position, appearance, and gestures") },
                    modifier = Modifier.clickable { onNavigateToHandle(id) }
                )
                Divider()
            }
        }
    }
}
