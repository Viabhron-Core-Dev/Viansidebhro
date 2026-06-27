package com.example

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
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
    var format by remember { mutableStateOf(prefs.getString("call_recorder_format", "MPEG_4") ?: "MPEG_4") }
    var quality by remember { mutableStateOf(prefs.getInt("call_recorder_quality", 128000)) }
    var saveFolder by remember { mutableStateOf(prefs.getString("call_recorder_save_folder", "") ?: "") }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            saveFolder = uri.toString()
            prefs.edit().putString("call_recorder_save_folder", uri.toString()).apply()
        }
    }

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
                    text = "Format & Quality",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp)
                )

                val formats = listOf("MPEG_4" to "MP3 / AAC (MPEG-4)", "THREE_GPP" to "WAV / AMR (3GPP)")
                formats.forEach { (formatValue, title) ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                            format = formatValue
                            prefs.edit().putString("call_recorder_format", formatValue).apply()
                        }
                    ) {
                        RadioButton(selected = format == formatValue, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(title)
                    }
                }

                val qualities = listOf(64000 to "Low (64 kbps)", 128000 to "Medium (128 kbps)", 256000 to "High (256 kbps)")
                qualities.forEach { (qualityValue, title) ->
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                            quality = qualityValue
                            prefs.edit().putInt("call_recorder_quality", qualityValue).apply()
                        }
                    ) {
                        RadioButton(selected = quality == qualityValue, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(title)
                    }
                }

                Divider()

                ListItem(
                    headlineContent = { Text("Save Folder") },
                    supportingContent = { Text(if (saveFolder.isEmpty()) "Default (Music/.Records)" else android.net.Uri.parse(saveFolder).path ?: "Custom Folder") },
                    trailingContent = {
                        Button(onClick = { safLauncher.launch(null) }) {
                            Text("Change")
                        }
                    }
                )

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
