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

sealed class SidebarItem {
    abstract val id: String
    abstract val label: String

    data class App(
        val packageName: String,
        override val label: String
    ) : SidebarItem() {
        override val id = "app:$packageName"
    }

    data class SystemAction(
        val action: String,
        override val label: String,
        val iconResId: Int
    ) : SidebarItem() {
        override val id = "system:$action"
    }

    data class VolumeAction(
        val stream: String,
        val action: String,
        override val label: String,
        val iconResId: Int
    ) : SidebarItem() {
        override val id = "volume:${stream}_$action"
    }

    data class MediaAction(
        val action: String,
        override val label: String,
        val iconResId: Int
    ) : SidebarItem() {
        override val id = "media:$action"
    }

    data class DisplayAction(
        val action: String,
        override val label: String,
        val iconResId: Int
    ) : SidebarItem() {
        override val id = "display:$action"
    }
}

val ALL_SYSTEM_ACTIONS = listOf(
    SidebarItem.SystemAction("back", "Back", android.R.drawable.ic_menu_revert),
    SidebarItem.SystemAction("home", "Home", android.R.drawable.ic_menu_compass),
    SidebarItem.SystemAction("lock_screen", "Lock screen", android.R.drawable.ic_lock_power_off),
    SidebarItem.SystemAction("notifications", "Notifications", android.R.drawable.ic_menu_info_details),
    SidebarItem.SystemAction("quick_settings", "Quick settings", android.R.drawable.ic_menu_manage),
    SidebarItem.SystemAction("recents", "Recents", android.R.drawable.ic_menu_recent_history),
    SidebarItem.SystemAction("screenshot", "Screenshot", android.R.drawable.ic_menu_camera),
    SidebarItem.SystemAction("splitscreen", "Splitscreen", android.R.drawable.ic_menu_gallery),
    SidebarItem.SystemAction("log_keeper", "Log Keeper", android.R.drawable.ic_menu_agenda),
    SidebarItem.SystemAction("ebook_reader", "eBook Reader", com.example.R.drawable.ic_library_books)
)

val ALL_VOLUME_ACTIONS = listOf(
    SidebarItem.VolumeAction("ringer", "vol_up", "Ringer Vol+", android.R.drawable.ic_lock_silent_mode_off),
    SidebarItem.VolumeAction("ringer", "vol_down", "Ringer Vol-", android.R.drawable.ic_lock_silent_mode_off),
    SidebarItem.VolumeAction("ringer", "mute", "Ringer Mute", android.R.drawable.ic_lock_silent_mode),
    SidebarItem.VolumeAction("ringer", "unmute", "Ringer Unmute", android.R.drawable.ic_lock_silent_mode_off),
    SidebarItem.VolumeAction("ringer", "toggle_mute", "Ringer Toggle Mute", android.R.drawable.ic_lock_silent_mode),
    SidebarItem.VolumeAction("ringer", "mode_silent", "Silent Mode", android.R.drawable.ic_lock_silent_mode),
    SidebarItem.VolumeAction("ringer", "mode_vibrate", "Vibrate Mode", android.R.drawable.ic_lock_silent_mode_off),
    SidebarItem.VolumeAction("ringer", "mode_normal", "Normal Mode", android.R.drawable.ic_lock_silent_mode_off),
    SidebarItem.VolumeAction("ringer", "mode_cycle", "Cycle Mode", android.R.drawable.ic_popup_sync),
    
    SidebarItem.VolumeAction("media", "vol_up", "Media Vol+", android.R.drawable.ic_media_play),
    SidebarItem.VolumeAction("media", "vol_down", "Media Vol-", android.R.drawable.ic_media_play),
    SidebarItem.VolumeAction("media", "mute", "Media Mute", android.R.drawable.ic_lock_silent_mode_off),
    SidebarItem.VolumeAction("media", "unmute", "Media Unmute", android.R.drawable.ic_lock_silent_mode),
    SidebarItem.VolumeAction("media", "toggle_mute", "Media Toggle Mute", android.R.drawable.ic_lock_silent_mode),

    SidebarItem.VolumeAction("notification", "vol_up", "Notif Vol+", android.R.drawable.ic_menu_info_details),
    SidebarItem.VolumeAction("notification", "vol_down", "Notif Vol-", android.R.drawable.ic_menu_info_details),
    SidebarItem.VolumeAction("notification", "mute", "Notif Mute", android.R.drawable.ic_lock_silent_mode_off),
    SidebarItem.VolumeAction("notification", "unmute", "Notif Unmute", android.R.drawable.ic_lock_silent_mode),

    SidebarItem.VolumeAction("alarm", "vol_up", "Alarm Vol+", android.R.drawable.ic_lock_idle_alarm),
    SidebarItem.VolumeAction("alarm", "vol_down", "Alarm Vol-", android.R.drawable.ic_lock_idle_alarm),
    SidebarItem.VolumeAction("alarm", "mute", "Alarm Mute", android.R.drawable.ic_lock_silent_mode_off),
    SidebarItem.VolumeAction("alarm", "unmute", "Alarm Unmute", android.R.drawable.ic_lock_silent_mode)
)

val ALL_MEDIA_ACTIONS = listOf(
    SidebarItem.MediaAction("play_pause", "Play/Pause", android.R.drawable.ic_media_play),
    SidebarItem.MediaAction("next", "Next", android.R.drawable.ic_media_next),
    SidebarItem.MediaAction("previous", "Previous", android.R.drawable.ic_media_previous),
    SidebarItem.MediaAction("stop", "Stop", android.R.drawable.ic_media_pause)
)

val ALL_DISPLAY_ACTIONS = listOf(
    SidebarItem.DisplayAction("torch_toggle", "Flashlight", android.R.drawable.ic_menu_camera),
    SidebarItem.DisplayAction("timeout_cycle", "Screen Timeout", android.R.drawable.ic_menu_recent_history),
    SidebarItem.DisplayAction("orientation_toggle", "Rotation Toggle", android.R.drawable.ic_menu_always_landscape_portrait)
)

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

    var activeItems = listOf<SidebarItem>()
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
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
        val result = mutableListOf<AppInfo>()
        for (resolveInfo in apps) {
            val packageName = resolveInfo.activityInfo.packageName
            val label = resolveInfo.loadLabel(pm).toString()
            result.add(AppInfo(packageName, label))
        }
        val distinctResult = result.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
        allInstalledApps = distinctResult
    }

    private suspend fun loadActiveApps() = withContext(Dispatchers.IO) {
        var jsonStr = prefs.getString("sidebar_apps", """["system:log_keeper", "system:ebook_reader"]""") ?: """["system:log_keeper", "system:ebook_reader"]"""
        if (jsonStr == "[]" || jsonStr == """["system:log_keeper"]""") {
            jsonStr = """["system:log_keeper", "system:ebook_reader"]"""
        }
        val jsonArray = JSONArray(jsonStr)
        val selectedIds = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            val itemStr = jsonArray.getString(i)
            if (!itemStr.contains(":")) {
                selectedIds.add("app:$itemStr")
            } else {
                selectedIds.add(itemStr)
            }
        }

        val result = mutableListOf<SidebarItem>()
        for (id in selectedIds) {
            if (id.startsWith("app:")) {
                val pkg = id.substringAfter("app:")
                val appInfo = allInstalledApps.find { it.packageName == pkg }
                if (appInfo != null) {
                    result.add(SidebarItem.App(appInfo.packageName, appInfo.label))
                }
            } else if (id.startsWith("system:")) {
                val action = id.substringAfter("system:")
                val sysAction = ALL_SYSTEM_ACTIONS.find { it.action == action }
                if (sysAction != null) {
                    result.add(SidebarItem.SystemAction(action, sysAction.label, sysAction.iconResId))
                }
            } else if (id.startsWith("volume:")) {
                val actionId = id.substringAfter("volume:")
                val volAction = ALL_VOLUME_ACTIONS.find { "${it.stream}_${it.action}" == actionId }
                if (volAction != null) {
                    result.add(SidebarItem.VolumeAction(volAction.stream, volAction.action, volAction.label, volAction.iconResId))
                }
            } else if (id.startsWith("media:")) {
                val actionId = id.substringAfter("media:")
                val mediaAction = ALL_MEDIA_ACTIONS.find { it.action == actionId }
                if (mediaAction != null) {
                    result.add(SidebarItem.MediaAction(actionId, mediaAction.label, mediaAction.iconResId))
                }
            } else if (id.startsWith("display:")) {
                val actionId = id.substringAfter("display:")
                val displayAction = ALL_DISPLAY_ACTIONS.find { it.action == actionId }
                if (displayAction != null) {
                    result.add(SidebarItem.DisplayAction(actionId, displayAction.label, displayAction.iconResId))
                }
            }
        }
        activeItems = result
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

    fun addItem(id: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val currentStr = prefs.getString("sidebar_apps", """["system:log_keeper", "system:ebook_reader"]""") ?: """["system:log_keeper", "system:ebook_reader"]"""
            val current = JSONArray(currentStr)
            for (i in 0 until current.length()) {
                var item = current.getString(i)
                if (!item.contains(":")) item = "app:$item"
                if (item == id) return@launch
            }
            current.put(id)
            prefs.edit().putString("sidebar_apps", current.toString()).apply()
            loadActiveApps()
            withContext(Dispatchers.Main) {
                onAppsUpdated()
            }
        }
    }

    fun removeItem(id: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val currentStr = prefs.getString("sidebar_apps", """["system:log_keeper", "system:ebook_reader"]""") ?: """["system:log_keeper", "system:ebook_reader"]"""
            val current = JSONArray(currentStr)
            val newArray = JSONArray()
            for (i in 0 until current.length()) {
                var item = current.getString(i)
                if (!item.contains(":")) item = "app:$item"
                if (item != id) {
                    newArray.put(item)
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
