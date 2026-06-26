package com.example

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallRecorderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("FloatingReaderPrefs", Context.MODE_PRIVATE)

    var enabled by remember { mutableStateOf(prefs.getBoolean("call_recorder_enabled", false)) }
    var audioSource by remember { mutableStateOf(prefs.getInt("call_recorder_audio_source", MediaRecorder.AudioSource.VOICE_RECOGNITION)) }

    val permissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG
    )
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.all { it.value }) {
            enabled = true
            prefs.edit().putBoolean("call_recorder_enabled", true).apply()
        } else {
            enabled = false
            prefs.edit().putBoolean("call_recorder_enabled", false).apply()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Call Recorder") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text("Enable Automatic Recording") },
                    supportingContent = { Text("Records calls to a hidden folder in Music/.Records") },
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    launcher.launch(permissions)
                                } else {
                                    enabled = false
                                    prefs.edit().putBoolean("call_recorder_enabled", false).apply()
                                }
                            }
                        )
                    }
                )
                Divider()
                
                Text(
                    text = "Audio Source",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                )

                val sources = listOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION to "Voice Recognition (Recommended)",
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION to "Voice Communication",
                    MediaRecorder.AudioSource.MIC to "Microphone",
                    MediaRecorder.AudioSource.VOICE_CALL to "Voice Call (May not work)"
                )

                sources.forEach { (sourceValue, title) ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = audioSource == sourceValue,
                            onClick = {
                                audioSource = sourceValue
                                prefs.edit().putInt("call_recorder_audio_source", sourceValue).apply()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(title)
                    }
                }
                
                Divider()
                
                Text(
                    text = "Privacy Note: The recordings are saved in the app's external music directory inside a hidden folder ('.Records') with a '.nomedia' file to prevent gallery or music apps from scanning the audio files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
