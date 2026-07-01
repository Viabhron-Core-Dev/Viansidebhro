package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.data.LibraryRepository
import com.example.service.FloatingReaderService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val multiPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val repository = LibraryRepository(this)
        lifecycleScope.launch {
            for (uri in uris) {
                repository.importBook(uri)
            }
            // re-launch service to library
            val intent = Intent(this@MainActivity, FloatingReaderService::class.java).apply {
                putExtra("OPEN_FROM_LAUNCHER", true)
            }
            androidx.core.content.ContextCompat.startForegroundService(this@MainActivity, intent)
            finishAndRemoveTask()
        }
    }

    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
                val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("scoped_directory_uri", uri.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val intent = Intent(this@MainActivity, FloatingReaderService::class.java).apply {
            putExtra("OPEN_FROM_LAUNCHER", true)
        }
        androidx.core.content.ContextCompat.startForegroundService(this@MainActivity, intent)
        finishAndRemoveTask()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val firstLaunch = prefs.getBoolean("first_launch", true)
        
        // Skip welcome if opening a file or picking a file
        if (intent.action == Intent.ACTION_VIEW || intent.getBooleanExtra("PICK_EPUB", false) || intent.getBooleanExtra("PICK_DIRECTORY", false)) {
            handleIntent(intent)
            return
        }

        if (!firstLaunch && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            val svcIntent = Intent(this@MainActivity, FloatingReaderService::class.java).apply {
                putExtra("OPEN_FROM_LAUNCHER", true)
            }
            androidx.core.content.ContextCompat.startForegroundService(this, svcIntent)
            finishAndRemoveTask()
            return
        }

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WelcomeScreen(
                        onContinue = {
                            prefs.edit().putBoolean("first_launch", false).apply()
                            val settingsIntent = Intent(this@MainActivity, SettingsActivity::class.java)
                            startActivity(settingsIntent)
                            finishAndRemoveTask()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val permIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(permIntent)
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_SHORT).show()
            finishAndRemoveTask()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                Toast.makeText(this, "Please grant notification permission", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            val repository = LibraryRepository(this)
            lifecycleScope.launch {
                val book = repository.importBook(uri)
                if (book != null) {
                    val svcIntent = Intent(this@MainActivity, FloatingReaderService::class.java).apply {
                        putExtra("BOOK_ID", book.id)
                    }
                    androidx.core.content.ContextCompat.startForegroundService(this@MainActivity, svcIntent)
                } else {
                    Toast.makeText(this@MainActivity, "Failed to import book", Toast.LENGTH_SHORT).show()
                }
                finishAndRemoveTask()
            }
            return
        }

        if (intent.getBooleanExtra("PICK_EPUB", false)) {
            multiPicker.launch("application/epub+zip")
            return
        }

        if (intent.getBooleanExtra("PICK_DIRECTORY", false)) {
            directoryPicker.launch(null)
            return
        }

        val serviceIntent = Intent(this, FloatingReaderService::class.java).apply {
            putExtra("OPEN_FROM_LAUNCHER", true)
        }
        androidx.core.content.ContextCompat.startForegroundService(this, serviceIntent)
        finishAndRemoveTask()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("super.onRequestPermissionsResult(requestCode, permissions, grantResults)"))
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                handleIntent(intent)
            } else {
                Toast.makeText(this, "Notification permission is required", Toast.LENGTH_SHORT).show()
                finishAndRemoveTask()
            }
        }
    }
}

