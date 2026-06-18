package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class AppInfo(
    val packageName: String,
    val label: String
)

class SidebarAppsManager(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val coroutineScope: CoroutineScope,
    private val onAppsUpdated: () -> Unit
) {

    var activeApps = listOf<AppInfo>()
        private set

    var allInstalledApps = listOf<AppInfo>()
        private set

    private var hasLoadedOnce = false

    val iconCache = object : LruCache<String, Bitmap>(80) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            coroutineScope.launch {
                iconCache.evictAll()
                loadAllAppsFromPackageManager()
                loadActiveApps()
                withContext(Dispatchers.Main) {
                    onAppsUpdated()
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        context.registerReceiver(packageReceiver, filter)
    }

    fun destroy() {
        context.unregisterReceiver(packageReceiver)
        iconCache.evictAll()
    }

    fun ensureLoaded() {
        if (!hasLoadedOnce) {
            coroutineScope.launch {
                loadAllAppsFromPackageManager()
                loadActiveApps()
                hasLoadedOnce = true
                withContext(Dispatchers.Main) {
                    onAppsUpdated()
                }
            }
        } else {
            onAppsUpdated()
        }
    }

    private suspend fun loadAllAppsFromPackageManager() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<AppInfo>()
        for (app in packages) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                val label = pm.getApplicationLabel(app).toString()
                result.add(AppInfo(app.packageName, label))
            }
        }
        result.sortBy { it.label.lowercase() }
        allInstalledApps = result
    }

    private suspend fun loadActiveApps() = withContext(Dispatchers.IO) {
        val jsonStr = prefs.getString("sidebar_apps", "[]") ?: "[]"
        val jsonArray = JSONArray(jsonStr)
        val selectedPackages = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            selectedPackages.add(jsonArray.getString(i))
        }

        val result = mutableListOf<AppInfo>()
        for (pkg in selectedPackages) {
            val appInfo = allInstalledApps.find { it.packageName == pkg }
            if (appInfo != null) {
                result.add(appInfo)
            }
        }
        activeApps = result
    }

    suspend fun loadIcon(packageName: String): Bitmap? = withContext(Dispatchers.IO) {
        iconCache.get(packageName)?.let { return@withContext it }

        val pm = context.packageManager
        return@withContext try {
            val icon = pm.getApplicationIcon(packageName)
            val bitmap = getBitmapFromDrawable(icon)
            if (bitmap != null) {
                iconCache.put(packageName, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        try {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        } catch (e: Exception) {
            return null
        }
    }

    fun addApp(packageName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val currentStr = prefs.getString("sidebar_apps", "[]") ?: "[]"
            val current = JSONArray(currentStr)
            for (i in 0 until current.length()) {
                if (current.getString(i) == packageName) return@launch
            }
            current.put(packageName)
            prefs.edit().putString("sidebar_apps", current.toString()).apply()
            loadActiveApps()
            withContext(Dispatchers.Main) {
                onAppsUpdated()
            }
        }
    }

    fun removeApp(packageName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val currentStr = prefs.getString("sidebar_apps", "[]") ?: "[]"
            val current = JSONArray(currentStr)
            val newArray = JSONArray()
            for (i in 0 until current.length()) {
                if (current.getString(i) != packageName) {
                    newArray.put(current.getString(i))
                }
            }
            prefs.edit().putString("sidebar_apps", newArray.toString()).apply()
            loadActiveApps()
            withContext(Dispatchers.Main) {
                onAppsUpdated()
            }
        }
    }
}
