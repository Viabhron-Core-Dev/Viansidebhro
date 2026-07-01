package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import com.example.R
import com.example.data.AppDatabase
import com.example.data.EpubBook
import com.example.util.AppLogger
import kotlinx.coroutines.*
import java.io.File
import android.text.*
import android.text.style.*
import android.graphics.Color
import java.util.Locale
import kotlin.math.max
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
import com.example.utils.PageManager
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class FloatingReaderService : Service() {
    private var serviceLifecycleOwner: ServiceLifecycleOwner? = null
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    
    // UI Refs
    private lateinit var tvWindowTitle: TextView
    private lateinit var tvContent: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var tvProgress: TextView
    private lateinit var toolbarContainer: View
    private lateinit var bubbleIcon: TextView
    private lateinit var windowContainer: View
    private lateinit var topDragBar: View

    private var cameFromLibrary = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentBook: EpubBook? = null
    private var currentChapterIndex: Int = 0
    private var chapterContent: String = ""

    private var isFolded = true
    private var savedWindowWidth = 800
    private var savedWindowHeight = 1200
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var foldedX = 0
    private var foldedY = 0
    private var savedWindowX = 0
    private var savedWindowY = 0
    private var librarySearchQuery: String = ""

    private lateinit var prefs: SharedPreferences
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        when (key) {
            "keep_screen_on" -> {
                if (::windowContainer.isInitialized) {
                    windowContainer.keepScreenOn = sharedPreferences.getBoolean("keep_screen_on", true)
                }
            }
            "use_scoped_dir" -> {
                if (overlayLibrary?.visibility == View.VISIBLE) {
                    loadLibraryBooks()
                }
            }
            "font_size_scale" -> {
                if (::tvContent.isInitialized) {
                    tvContent.textSize = 16f * sharedPreferences.getFloat("font_size_scale", 1.0f)
                }
            }
            "use_dark_theme" -> {
                applyThemeFromPrefs()
            }
            "trigger_position", "sidebar_position" -> {
                triggerHandleView?.updatePosition()
                readerHandleView?.updatePosition()
            }
            "handle_sidebar_y", "handle_sidebar_width", "handle_sidebar_height", "handle_sidebar_color", "handle_sidebar_shape" -> {
                triggerHandleView?.updatePosition()
            }
            "handle_reader_y", "handle_reader_width", "handle_reader_height", "handle_reader_color", "handle_reader_shape" -> {
                readerHandleView?.updatePosition()
            }
            "sidebar_pages", "sidebar_default_page_index",
            "sidebar_columns", "sidebar_width", "sidebar_height", 
            "sidebar_wrap_content", "sidebar_transparency", "sidebar_position_left" -> {
                val wasAttached = sidebarView?.windowToken != null
                sidebarView?.detach()
                sidebarView = null
                if (wasAttached) {
                    showSidebar()
                }
            }
            "reader_handle_enabled" -> {
                if (sharedPreferences.getBoolean("reader_handle_enabled", false)) {
                    readerHandleView?.attach()
                } else {
                    readerHandleView?.detach()
                }
            }
            "speed_indicator_enabled" -> {
                netSpeedEnabled = sharedPreferences.getBoolean("speed_indicator_enabled", false)
                if (netSpeedEnabled) {
                    if (netSpeedManager == null) {
                        netSpeedManager = NetSpeedManager(this@FloatingReaderService, prefs, 
                            onSpeedUpdate = { down, up ->
                                downSpeed = down
                                upSpeed = up
                                updatePersistentNotification()
                            },
                            onDailyDataUpdate = { mobile, wifi ->
                                mobileMb = mobile
                                wifiMb = wifi
                                updatePersistentNotification()
                            }
                        )
                    }
                    netSpeedManager?.start()
                } else {
                    netSpeedManager?.stop()
                }
                updatePersistentNotification()
            }
            "call_recorder_enabled" -> {
                if (sharedPreferences.getBoolean("call_recorder_enabled", false)) {
                    callRecorderManager?.startListening()
                } else {
                    callRecorderManager?.stopListening()
                }
            }
        }
    }
    
    private fun applyThemeFromPrefs() {
        if (!::windowContainer.isInitialized) return
        val isDark = prefs.getBoolean("use_dark_theme", true)
        val bgColor = if (isDark) android.graphics.Color.parseColor("#222222") else android.graphics.Color.WHITE
        val txColor = if (isDark) android.graphics.Color.parseColor("#DDDDDD") else android.graphics.Color.BLACK
        
        windowContainer.setBackgroundColor(bgColor)
        tvContent.setTextColor(txColor)
        overlayChapters?.setBackgroundColor(bgColor)
        overlayLibrary?.setBackgroundColor(bgColor)
    }
    private var triggerHandleView: TriggerHandleView? = null
    private var readerHandleView: ReaderHandleView? = null
    private var sidebarView: SidebarView? = null
    private var sidebarPagesList = mutableListOf<View>()
    private var sidebarDefaultIndex = 0
    private lateinit var appsManager: SidebarAppsManager
    private var callRecorderManager: CallRecorderManager? = null
    private var appPickerOverlayView: AppPickerOverlayView? = null
    private var intentPickerOverlayView: IntentPickerOverlayView? = null
    private var addElementOverlayView: AddElementOverlayView? = null
    private var sidebarEditOverlayView: SidebarEditOverlayView? = null
    
    private var netSpeedManager: NetSpeedManager? = null
    private var screenStateReceiver: android.content.BroadcastReceiver? = null
    private var netSpeedEnabled = false
    private var mobileMb: Long = 0
    private var wifiMb: Long = 0
    private var downSpeed: Long = 0
    private var upSpeed: Long = 0
    
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isSpeaking = false
    private lateinit var btnTts: ImageView

    // Auto Scroll State
    private var isAutoScrolling = false
    private val scrollHandler = Handler(Looper.getMainLooper())
    private var mediaSession: android.media.session.MediaSession? = null

    private val scrollRunnable = object : Runnable {
        override fun run() {
            if (isAutoScrolling) {
                scrollView.smoothScrollBy(0, 2)
                scrollHandler.postDelayed(this, 30) // light speed modifier
            }
        }
    }

    companion object {
        var instance: FloatingReaderService? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        serviceLifecycleOwner = ServiceLifecycleOwner()
        serviceLifecycleOwner?.onCreate()
        serviceLifecycleOwner?.onStart()
        serviceLifecycleOwner?.onResume()
        
        prefs = getSharedPreferences("FloatingReaderPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_handle_edit_mode", false).apply()

        // Start Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "reader_channel",
                "Floating Reader",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = android.content.Intent(this, com.example.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, notificationIntent, android.app.PendingIntent.FLAG_IMMUTABLE)

        val notification = androidx.core.app.NotificationCompat.Builder(this, "reader_channel")
            .setContentTitle("LiteReader")
            .setContentText("Reading active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .build()
            
        mediaSession = android.media.session.MediaSession(this, "FloatingReader")
        mediaSession?.isActive = true

        if (Build.VERSION.SDK_INT >= 29) {
            val foregroundServiceTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var types = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    types = types or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                types
            } else {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            }
            startForeground(1, notification, foregroundServiceTypes)
        } else {
            startForeground(1, notification)
        }

        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        loadSettingsFromPrefs()
        savedWindowWidth = prefs.getInt("win_w", 800)
        savedWindowHeight = prefs.getInt("win_h", 1200)
        savedWindowX = prefs.getInt("win_x", 0)
        savedWindowY = prefs.getInt("win_y", 0)
        foldedX = prefs.getInt("fold_x", 0)
        foldedY = prefs.getInt("fold_y", 100)
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupFloatingView()

        appsManager = SidebarAppsManager(this, prefs, serviceScope) {
            appsPageViews.forEach { it.updateData(appsManager.activeItems) }
        }
        
        callRecorderManager = CallRecorderManager(this, prefs)
        callRecorderManager?.startListening()
        
        rebuildSidebarPages()
        appsManager.ensureLoaded()

        triggerHandleView = TriggerHandleView(this, prefs, windowManager) {
            android.util.Log.d("VianSide", "trigger tapped")
            showSidebar()
        }
        triggerHandleView?.attach()

        readerHandleView = ReaderHandleView(this, prefs, windowManager) {
            android.util.Log.d("VianSide", "reader trigger tapped")
            toggleReader()
        }
        readerHandleView?.attach()
        
        setupNetSpeed()
        updatePersistentNotification()
    }
    
    fun toggleReader() {
        if (isFolded) {
            val lastBook = prefs.getInt("last_book_id", -1)
            if (currentBook == null && lastBook != -1) loadBook(lastBook)
            setFolded(false)
        } else {
            setFolded(true)
        }
    }
    
    private fun setupNetSpeed() {
        val dailyMobileRx = prefs.getLong("daily_mobile_rx", 0)
        val dailyMobileTx = prefs.getLong("daily_mobile_tx", 0)
        val dailyWifiRx = prefs.getLong("daily_wifi_rx", 0)
        val dailyWifiTx = prefs.getLong("daily_wifi_tx", 0)
        mobileMb = (dailyMobileRx + dailyMobileTx) / (1024 * 1024)
        wifiMb = (dailyWifiRx + dailyWifiTx) / (1024 * 1024)

        netSpeedEnabled = prefs.getBoolean("speed_indicator_enabled", false)
        if (netSpeedEnabled) {
            netSpeedManager = NetSpeedManager(this, prefs, 
                onSpeedUpdate = { down, up ->
                    downSpeed = down
                    upSpeed = up
                    updatePersistentNotification()
                },
                onDailyDataUpdate = { mobile, wifi ->
                    mobileMb = mobile
                    wifiMb = wifi
                    updatePersistentNotification()
                }
            )
            netSpeedManager?.start()
        }
        
        screenStateReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        if (netSpeedEnabled) netSpeedManager?.start()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        if (netSpeedEnabled) netSpeedManager?.stop()
                    }
                }
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
        
        // Setup AlarmManager for midnight reset
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(this, MidnightResetReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        
        alarmManager.setRepeating(
            android.app.AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            android.app.AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
    
    private fun updatePersistentNotification() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        
        val notificationIntent = Intent(this, com.example.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(this, 0, notificationIntent, android.app.PendingIntent.FLAG_IMMUTABLE)

        val settingsIntent = Intent(this, com.example.SettingsActivity::class.java).apply {
            putExtra("start_route", "netspeed")
        }
        val settingsPendingIntent = android.app.PendingIntent.getActivity(this, 1, settingsIntent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)

        val speedUnits = prefs.getString("speed_units", "Auto") ?: "Auto"
        val formatSpeed = { bytesPerSec: Long ->
            val kbps = bytesPerSec / 1024.0
            val mbps = kbps / 1024.0
            when (speedUnits) {
                "KB/s" -> String.format("%.0f KB/s", kotlin.math.max(0.0, kbps))
                "MB/s" -> String.format("%.2f MB/s", kotlin.math.max(0.0, mbps))
                else -> if (kbps >= 1024) String.format("%.1f MB/s", mbps) else String.format("%.0f KB/s", kotlin.math.max(0.0, kbps))
            }
        }

        val dataUnits = prefs.getString("data_units", "Auto") ?: "Auto"
        val totalMb = mobileMb + wifiMb
        val dataText = when(dataUnits) {
            "MB" -> "Data: $totalMb MB"
            "GB" -> String.format("Data: %.2f GB", totalMb / 1000.0)
            "GiB" -> String.format("Data: %.2f GiB", totalMb / 1024.0)
            else -> if (totalMb >= 1024) String.format("Data: %.2f GiB", totalMb / 1024.0) else "Data: $totalMb MB"
        }

        val notificationBuilder = androidx.core.app.NotificationCompat.Builder(this, "reader_channel")
            .setContentTitle(dataText)
            .setContentText("Down: ${formatSpeed(downSpeed)}   Up: ${formatSpeed(upSpeed)}")
            .setContentIntent(settingsPendingIntent)
            .setOnlyAlertOnce(true)

        if (netSpeedEnabled) {
            val speedIcon = createSpeedIcon(kotlin.math.max(downSpeed, upSpeed))
            notificationBuilder.setSmallIcon(speedIcon)
        } else {
            notificationBuilder.setSmallIcon(android.R.drawable.ic_media_play)
        }
            
        manager.notify(1, notificationBuilder.build())
    }

    private fun createSpeedIcon(speedBytes: Long): androidx.core.graphics.drawable.IconCompat {
        // High resolution for sharpness (system will scale it down smoothly for status bar)
        val size = 200
        
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
        }
        
        val kbps = speedBytes / 1024.0
        val mbps = kbps / 1024.0
        val valueStr: String
        val unitStr: String
        
        val speedUnits = prefs.getString("speed_units", "Auto") ?: "Auto"
        when (speedUnits) {
            "KB/s" -> {
                valueStr = String.format("%.0f", kotlin.math.max(0.0, kbps))
                unitStr = "KB/s"
            }
            "MB/s" -> {
                valueStr = String.format("%.2f", kotlin.math.max(0.0, mbps))
                unitStr = "MB/s"
            }
            else -> {
                if (kbps >= 1024) {
                    valueStr = String.format("%.1f", mbps)
                    unitStr = "MB/s"
                } else {
                    valueStr = String.format("%.0f", kotlin.math.max(0.0, kbps))
                    unitStr = "KB/s"
                }
            }
        }
        
        // Use sans-serif-medium like system time
        textPaint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        var valueTextSize = size * 0.55f
        textPaint.textSize = valueTextSize
        while (textPaint.measureText(valueStr) > size - 8f && valueTextSize > size * 0.2f) {
            valueTextSize -= 2f
            textPaint.textSize = valueTextSize
        }
        // Top text
        val valueY = -textPaint.ascent() + size * 0.05f
        canvas.drawText(valueStr, size / 2f, valueY, textPaint)
        
        var unitTextSize = size * 0.35f
        textPaint.textSize = unitTextSize
        while (textPaint.measureText(unitStr) > size - 8f && unitTextSize > size * 0.1f) {
            unitTextSize -= 2f
            textPaint.textSize = unitTextSize
        }
        // Bottom text
        val unitY = size * 0.95f - textPaint.descent()
        canvas.drawText(unitStr, size / 2f, unitY, textPaint)
        
        return androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap)
    }
    
    private val appsPageViews = mutableListOf<AppsPageView>()

    private fun rebuildSidebarPages() {
        val pageConfigs = PageManager.getPages(prefs)
        sidebarDefaultIndex = PageManager.getDefaultPageIndex(prefs)
        sidebarPagesList.clear()
        appsPageViews.clear()
        
        pageConfigs.forEach { config ->
            val pageView = when (config.type) {
                "apps" -> {
                    var p: AppsPageView? = null
                    p = AppsPageView(this, appsManager, serviceScope,
                        onCloseSidebar = { sidebarView?.detach() },
                        onHeightChanged = { newHeight ->
                            // Only update height if this is the currently selected page
                            if (sidebarView != null && p != null && sidebarPagesList.indexOf(p!!) == sidebarView!!.getCurrentPageIndex()) {
                                sidebarView?.updatePageStyles(config, newHeight)
                            }
                        }
                    )
                    p.updateData(appsManager.activeItems)
                    appsPageViews.add(p)
                    p
                }
                "scheduler" -> SchedulerPageView(this, serviceScope)
                "calculator" -> CalculatorPageView(this)
                "compass" -> CompassPageView(this)
                "notifications" -> {
                    var p: NotificationPageView? = null
                    p = NotificationPageView(this) { newHeight ->
                        if (sidebarView != null && p != null && sidebarPagesList.indexOf(p!!) == sidebarView!!.getCurrentPageIndex()) {
                            sidebarView?.updatePageStyles(config, newHeight)
                        }
                    }
                    p
                }
                else -> {
                    TextView(this).apply {
                        text = "${config.title} coming soon..."
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        textSize = 16f
                    }
                }
            }
            sidebarPagesList.add(pageView)
        }
        
        // Ensure index is valid
        if (sidebarDefaultIndex >= sidebarPagesList.size) {
            sidebarDefaultIndex = 0
        }
    }
    
    private fun showSidebar() {
        if (sidebarView == null) {
            rebuildSidebarPages()
            sidebarView = SidebarView(this, prefs, windowManager, sidebarPagesList, PageManager.getPages(prefs), sidebarDefaultIndex,
                onAddClicked = { showSidebarEditOverlay() },
                onClose = { sidebarView?.detach() }
            )
            serviceLifecycleOwner?.let {
                sidebarView?.setViewTreeLifecycleOwner(it)
                sidebarView?.setViewTreeViewModelStoreOwner(it)
                sidebarView?.setViewTreeSavedStateRegistryOwner(it)
            }
            val defaultPage = sidebarPagesList.getOrNull(sidebarDefaultIndex)
            val defaultPageConfig = PageManager.getPages(prefs).getOrNull(sidebarDefaultIndex)
            if (defaultPage is AppsPageView) {
                sidebarView?.updatePageStyles(defaultPageConfig, (defaultPage).getCurrentHeightPx())
            } else if (defaultPage is NotificationPageView) {
                sidebarView?.updatePageStyles(defaultPageConfig, (defaultPage).getCurrentHeightPx())
            } else if (defaultPage != null) {
                val density = resources.displayMetrics.density
                sidebarView?.updatePageStyles(defaultPageConfig, (450 * density).toInt())
            }
        }
        sidebarView?.attach()
    }
    
    fun openSidebarPage(type: String) {
        showSidebar()
        val pageConfigs = PageManager.getPages(prefs)
        val index = pageConfigs.indexOfFirst { it.type == type }
        if (index != -1) {
            sidebarView?.goToPage(index)
        }
    }

    fun showSidebarEditOverlay() {
        sidebarEditOverlayView?.detach()
        sidebarEditOverlayView = SidebarEditOverlayView(
            this, appsManager, windowManager, serviceScope,
            onAddClicked = { 
                showAddElementOverlayForSelection { id ->
                    sidebarEditOverlayView?.localIds?.add(id)
                    sidebarEditOverlayView?.refresh()
                }
            },
            onClose = { sidebarEditOverlayView?.detach() }
        )
        sidebarEditOverlayView?.attach()
    }

    fun showAddElementOverlay(targetFolderUuid: String? = null) {
        addElementOverlayView?.detach()
        addElementOverlayView = AddElementOverlayView(
            this, appsManager, windowManager, targetFolderUuid,
            onClose = { addElementOverlayView?.detach() },
            onAppSelected = { folderUuid -> 
                addElementOverlayView?.detach()
                showAppPicker(folderUuid) 
            }
        )
        addElementOverlayView?.attach()
    }

    fun showAddElementOverlayForSelection(onElementSelected: (String) -> Unit) {
        addElementOverlayView?.detach()
        addElementOverlayView = AddElementOverlayView(
            this, appsManager, windowManager, null,
            onClose = { addElementOverlayView?.detach() },
            onAppSelected = { _ -> 
                addElementOverlayView?.detach()
                showAppPickerForSelection(onElementSelected) 
            },
            onElementSelected = { id ->
                addElementOverlayView?.detach()
                onElementSelected(id)
            }
        )
        addElementOverlayView?.attach()
    }

    private fun showAppPicker(targetFolderUuid: String? = null) {
        appPickerOverlayView?.detach()
        appPickerOverlayView = AppPickerOverlayView(this, appsManager, serviceScope, windowManager, targetFolderUuid) {
            appPickerOverlayView?.detach()
        }
        appPickerOverlayView?.attach()
    }

    fun showIntentPicker(targetFolderUuid: String? = null, onElementSelected: ((String) -> Unit)? = null) {
        intentPickerOverlayView?.detach()
        intentPickerOverlayView = IntentPickerOverlayView(this, appsManager, serviceScope, windowManager, targetFolderUuid, onIntentSelected = onElementSelected) {
            intentPickerOverlayView?.detach()
        }
        intentPickerOverlayView?.attach()
    }

    private fun showAppPickerForSelection(onElementSelected: (String) -> Unit) {
        appPickerOverlayView?.detach()
        appPickerOverlayView = AppPickerOverlayView(this, appsManager, serviceScope, windowManager, null, onAppSelected = { id ->
            appPickerOverlayView?.detach()
            onElementSelected(id)
        }) {
            appPickerOverlayView?.detach()
        }
        appPickerOverlayView?.attach()
    }

    fun executeElementAction(id: String) {
        if (id.startsWith("app:")) {
            val pkg = id.removePrefix("app:")
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { startActivity(launchIntent) } catch (e: Exception) {}
            }
        } else if (id.startsWith("system:")) {
            val action = id.removePrefix("system:")
            if (action == "log_keeper") {
                val intent = Intent(this, com.example.LogKeeperActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else if (action == "ebook_reader") {
                val intent = Intent(this, FloatingReaderService::class.java)
                intent.putExtra("UNFOLD", true)
                startService(intent)
            } else if (action == "settings") {
                val intent = Intent(this, com.example.SettingsActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                val service = VianSideAccessibilityService.instance
                if (service != null && service.performAction(action)) {
                    // success
                } else {
                    android.widget.Toast.makeText(this, "Please enable VianSide Accessibility Service", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } else if (id.startsWith("volume:")) {
            val actionId = id.removePrefix("volume:")
            try {
                val streamStr = actionId.split("_")[0]
                val actionStr = actionId.split("_")[1]
                MediaVolumeHandler.handleVolumeAction(this, streamStr, actionStr)
            } catch (e: Exception) {}
        } else if (id.startsWith("media:")) {
            val actionId = id.removePrefix("media:")
            MediaVolumeHandler.handleMediaAction(this, actionId)
        } else if (id.startsWith("display:")) {
            val actionId = id.removePrefix("display:")
            DisplayHandler.handleDisplayAction(this, actionId)
        } else if (id.startsWith("intent:")) {
            val componentStr = id.removePrefix("intent:")
            val parts = componentStr.split("/")
            if (parts.size == 2) {
                val launchIntent = Intent()
                launchIntent.setComponent(android.content.ComponentName(parts[0], parts[1]))
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { startActivity(launchIntent) } catch (e: Exception) {}
            }
        } else if (id.startsWith("shortcut:")) {
            try {
                val parts = id.split(":", limit = 3)
                val jsonStr = parts[2]
                val obj = org.json.JSONObject(jsonStr)
                val url = obj.getString("url")
                val launchIntent = if (url.startsWith("intent:")) {
                    Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                } else {
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                }
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } catch (e: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ADD_ELEMENT") {
            val elementId = intent.getStringExtra("element_id") ?: return START_NOT_STICKY
            val folderUuid = intent.getStringExtra("FOLDER_UUID")
            val isElementCallback = intent.getBooleanExtra("IS_ELEMENT_CALLBACK", false)
            if (isElementCallback) {
                // Not supported yet directly returning to callback, we could broadcast
            } else if (folderUuid != null) {
                appsManager.addItemToFolder(folderUuid, elementId)
            } else {
                appsManager.addItem(elementId)
            }
            return START_NOT_STICKY
        }
        
        if (intent?.action == "SELECT_ELEMENT_FOR_HANDLE") {
            val prefix = intent.getStringExtra("handle_prefix") ?: return START_NOT_STICKY
            val gesture = intent.getStringExtra("gesture") ?: return START_NOT_STICKY
            showAddElementOverlayForSelection { selectedId ->
                prefs.edit().putString("${prefix}$gesture", "open_element:$selectedId").apply()
            }
            return START_NOT_STICKY
        }
        
        val bookId = intent?.getIntExtra("BOOK_ID", -1) ?: -1
        val fromLauncher = intent?.getBooleanExtra("OPEN_FROM_LAUNCHER", false) ?: false
        val unfold = intent?.getBooleanExtra("UNFOLD", false) ?: false
        
        if (fromLauncher) {
            val lastBook = prefs.getInt("last_book_id", -1)
            if (lastBook != -1) {
                loadBook(lastBook)
                setFolded(false)
            } else {
                openLibraryView()
                setFolded(false)
            }
        } else if (bookId != -1) {
            loadBook(bookId)
            setFolded(false)
        } else if (unfold) {
            val lastBook = prefs.getInt("last_book_id", -1)
            if (currentBook == null && lastBook != -1) loadBook(lastBook)
            setFolded(false)
        }
        return START_NOT_STICKY
    }

    private fun setupFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_reader, null)
        
        serviceLifecycleOwner?.let {
            floatingView.setViewTreeLifecycleOwner(it)
            floatingView.setViewTreeViewModelStoreOwner(it)
            floatingView.setViewTreeSavedStateRegistryOwner(it)
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = prefs.getInt("win_x", 100)
        layoutParams.y = prefs.getInt("win_y", 100)

        windowManager.addView(floatingView, layoutParams)

        updateKeepScreenOn()

        initViews()
        setupListeners()
        setFolded(true)
    }

    private var overlayLibrary: View? = null
    private var overlayChapters: View? = null
    private var overlayBookmarks: View? = null
    private var overlaySearch: View? = null
    private var overlaySearchResults: View? = null
    private var overlayNotes: View? = null
    private lateinit var bottomWindowControls: View
    private var listLibrary: androidx.recyclerview.widget.RecyclerView? = null
    private var listChapters: androidx.recyclerview.widget.RecyclerView? = null
    private var listBookmarks: androidx.recyclerview.widget.RecyclerView? = null

    private fun initViews() {
        topDragBar = floatingView.findViewById(R.id.top_drag_bar)
        tvWindowTitle = floatingView.findViewById(R.id.tv_window_title)
        tvContent = floatingView.findViewById(R.id.tv_content)
        scrollView = floatingView.findViewById(R.id.scroll_view)
        tvProgress = floatingView.findViewById(R.id.tv_progress)
        toolbarContainer = floatingView.findViewById(R.id.toolbar_container)
        bubbleIcon = floatingView.findViewById(R.id.bubble_icon)
        
        applyThemeFromPrefs()
        tvContent.textSize = 16f * prefs.getFloat("font_size_scale", 1.0f)
        
        var startX = 0f
        var startY = 0f
        var startTime = 0L

        val touchListener = View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    startTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val endY = event.y
                    val endX = event.x
                    val dy = startY - endY 
                    val dx = startX - endX
                    val dt = System.currentTimeMillis() - startTime
                    
                    // Delay slightly to let TextView process its own selection
                    v.postDelayed({
                        val hasSelection = tvContent.selectionStart != tvContent.selectionEnd
                        val customMenu = floatingView.findViewById<View>(R.id.custom_selection_menu)
                        if (hasSelection) {
                            customMenu?.visibility = View.VISIBLE
                        } else {
                            customMenu?.visibility = View.GONE
                        }

                        if (Math.abs(dy) > 150) {
                            if (dy > 150 && !scrollView.canScrollVertically(1)) {
                                navigateChapter(1)
                            } else if (dy < -150 && !scrollView.canScrollVertically(-1)) {
                                navigateChapter(-1, scrollToBottom = true)
                            }
                        } else if (dt < 200 && Math.abs(dy) < 30 && Math.abs(dx) < 30 && !hasSelection) {
                            // Tap
                            if (overlayChapters?.visibility == View.VISIBLE) {
                                hideOverlays()
                            } else {
                                val tapToTurn = prefs.getBoolean("tap_to_turn", true)
                                val width = v.width
                                if (tapToTurn && endX < width * 0.3f) {
                                    scrollView.smoothScrollBy(0, -(v.height - 100))
                                } else if (tapToTurn && endX > width * 0.7f) {
                                    scrollView.smoothScrollBy(0, v.height - 100)
                                } else {
                                    val isVisible = toolbarContainer.visibility == View.VISIBLE
                                    toolbarContainer.visibility = if (isVisible) View.GONE else View.VISIBLE
                                    updateBottomControlsVisibility()
                                }
                            }
                        }
                    }, 50)
                }
            }
            false
        }

        scrollView.setOnTouchListener(touchListener)
        tvContent.setOnTouchListener(touchListener)
        
        // Prevent default action mode from crashing in overlay
        tvContent.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                return true // Return true so selection remains, we just don't handle its menu
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                menu?.clear() // Clear default items
                return false
            }
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {
                 floatingView.findViewById<View>(R.id.custom_selection_menu)?.visibility = View.GONE
            }
        }
        windowContainer = floatingView.findViewById(R.id.window_container)
        btnTts = floatingView.findViewById(R.id.btn_tts)
        bottomWindowControls = floatingView.findViewById(R.id.bottom_window_controls)
        
        // Initialize XML overlays
        // Search & Bookmarks are initialized on demand

        startAutoSaveTimer()
        
        // Auto-sync all library books to Tracker
        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@FloatingReaderService)
            val allEpubs = db.epubDao().getAllBooks()
            val trackerBooks = db.trackerDao().getAllBooks()
            for (epub in allEpubs) {
                val existingTracker = trackerBooks.firstOrNull { it.title == epub.title }
                if (existingTracker == null) {
                    val newTracker = com.example.data.TrackerBook(
                        title = epub.title,
                        author = "",
                        totalChapters = epub.totalChapters,
                        readChapters = epub.lastReadChapter,
                        addedTimestamp = epub.addedTimestamp,
                        lastUpdatedTimestamp = epub.lastOpenedTimestamp
                    )
                    db.trackerDao().insertBook(newTracker)
                } else if (existingTracker.readChapters < epub.lastReadChapter || existingTracker.totalChapters < epub.totalChapters) {
                    val updatedTracker = existingTracker.copy(
                        readChapters = Math.max(existingTracker.readChapters, epub.lastReadChapter),
                        totalChapters = Math.max(existingTracker.totalChapters, epub.totalChapters),
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                    db.trackerDao().updateBook(updatedTracker)
                }
            }
        }
    }

    private var toastJob: kotlinx.coroutines.Job? = null

    private fun showToast(message: String) {
        serviceScope.launch(Dispatchers.Main) {
            val tvToast = floatingView.findViewById<TextView>(R.id.tv_custom_toast)
            if (tvToast != null) {
                tvToast.text = message
                tvToast.visibility = View.VISIBLE
                toastJob?.cancel()
                toastJob = launch {
                    kotlinx.coroutines.delay(2500)
                    tvToast.visibility = View.GONE
                }
            } else {
                Toast.makeText(this@FloatingReaderService, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncWindowStates() {
        updateTopDragBarVisibility()
        updateBottomControlsVisibility()
        updateWindowFocusAbility()
    }
    
    private fun hideOverlays() {
        overlayLibrary?.visibility = View.GONE
        overlayChapters?.visibility = View.GONE
        overlayBookmarks?.visibility = View.GONE
        overlayNotes?.visibility = View.GONE
        overlaySearch?.visibility = View.GONE
        overlaySearchResults?.visibility = View.GONE
        scrollView.visibility = View.VISIBLE
        syncWindowStates()
    }
    
    private fun updateBottomControlsVisibility() {
        if (!::bottomWindowControls.isInitialized) return
        
        if (isFullScreen) {
            bottomWindowControls.visibility = View.GONE
            return
        }
        
        val isAnyOverlayVisible = (overlayLibrary?.visibility == View.VISIBLE ||
                overlayChapters?.visibility == View.VISIBLE ||
                overlayBookmarks?.visibility == View.VISIBLE ||
                overlayNotes?.visibility == View.VISIBLE ||
                overlaySearch?.visibility == View.VISIBLE ||
                overlaySearchResults?.visibility == View.VISIBLE)
                
        if (!isAnyOverlayVisible && toolbarContainer.visibility == View.VISIBLE) {
            bottomWindowControls.visibility = View.GONE
        } else {
            bottomWindowControls.visibility = View.VISIBLE
        }
    }
    
    private fun updateWindowFocusAbility() {
        val searchVisible = overlaySearch?.visibility == View.VISIBLE || 
            (overlayNotes?.findViewById<View>(R.id.note_editor_container)?.visibility == View.VISIBLE) ||
            (floatingView.findViewById<View>(R.id.et_search_library)?.visibility == View.VISIBLE) ||
            (floatingView.findViewById<View>(R.id.et_search_notes)?.visibility == View.VISIBLE)
            
        if (searchVisible) {
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (this::windowManager.isInitialized && floatingView.parent != null) {
            windowManager.updateViewLayout(floatingView, layoutParams)
        }
    }

    private fun updateTopDragBarVisibility() {
        if (overlayLibrary?.visibility == View.VISIBLE ||
            overlayBookmarks?.visibility == View.VISIBLE ||
            overlayNotes?.visibility == View.VISIBLE) {
            topDragBar.visibility = View.GONE
        } else {
            topDragBar.visibility = View.VISIBLE
        }
    }

    private var currentLibraryTab = "Recent"
    private var currentExplorerDir: java.io.File? = null
    private var rootExplorerDir: java.io.File? = null
    private var explorerSortByName: Boolean = true
    private var explorerSortAscending: Boolean = true

    private var isFullScreen = false
    private var preFullScreenX = 0
    private var preFullScreenY = 0
    private var preFullScreenWidth = 0
    private var preFullScreenHeight = 0

    private fun toggleFullScreen() {
        val windowControls = floatingView.findViewById<View>(R.id.bottom_window_controls)
        val closeFullscreenContainer = floatingView.findViewById<View>(R.id.fullscreen_close_container)
        if (!isFullScreen) {
            preFullScreenX = layoutParams.x
            preFullScreenY = layoutParams.y
            preFullScreenWidth = layoutParams.width
            preFullScreenHeight = layoutParams.height
            layoutParams.x = 0
            layoutParams.y = 0
            layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
            layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
            isFullScreen = true
            windowControls?.visibility = View.GONE
            closeFullscreenContainer?.visibility = View.VISIBLE
        } else {
            layoutParams.x = preFullScreenX
            layoutParams.y = preFullScreenY
            layoutParams.width = preFullScreenWidth
            layoutParams.height = preFullScreenHeight
            isFullScreen = false
            windowControls?.visibility = View.VISIBLE
            closeFullscreenContainer?.visibility = View.GONE
        }
        windowManager.updateViewLayout(floatingView, layoutParams)
    }

    private fun updateKeepScreenOn() {
        if (prefs.getBoolean("keep_screen_on", false)) {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        } else {
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON.inv()
        }
        if (this::windowManager.isInitialized) {
            windowManager.updateViewLayout(floatingView, layoutParams)
        }
    }

    private fun loadSettingsFromPrefs() {
        currentLibraryTab = prefs.getString("last_library_tab", "Recent") ?: "Recent"
        val lastDirPath = prefs.getString("last_explorer_dir", null)
        explorerSortByName = prefs.getBoolean("explorer_sort_name", true)
        explorerSortAscending = prefs.getBoolean("explorer_sort_asc", true)
        if (lastDirPath != null) {
            val dir = java.io.File(lastDirPath)
            if (dir.exists() && dir.isDirectory) {
                rootExplorerDir = android.os.Environment.getExternalStorageDirectory()
                explorerStack.clear()
                if (rootExplorerDir != null) {
                    explorerStack.add(rootExplorerDir!!)
                    var current: java.io.File? = dir
                    val temp = mutableListOf<java.io.File>()
                    while (current != null && current.absolutePath != rootExplorerDir?.absolutePath) {
                        temp.add(0, current)
                        current = current.parentFile
                    }
                    if (current?.absolutePath == rootExplorerDir?.absolutePath) {
                        explorerStack.addAll(temp)
                    } else {
                        explorerStack.clear()
                        explorerStack.add(rootExplorerDir!!)
                        explorerStack.add(dir) // simple stack recovery
                    }
                }
            }
        }
    }

    private fun saveLibraryState() {
        val editor = prefs.edit()
        editor.putString("last_library_tab", currentLibraryTab)
        editor.putBoolean("explorer_sort_name", explorerSortByName)
        editor.putBoolean("explorer_sort_asc", explorerSortAscending)
        explorerStack.lastOrNull()?.let {
            editor.putString("last_explorer_dir", it.absolutePath)
        }
        editor.apply()
    }

    private fun loadLibraryBooks() {
        val useScopedDir = prefs.getBoolean("use_scoped_dir", true)
        val btnRecent = floatingView.findViewById<Button>(R.id.btn_tab_recent)
        val btnImported = floatingView.findViewById<Button>(R.id.btn_tab_imported)
        val barExplorerTools = floatingView.findViewById<View>(R.id.bar_explorer_tools)
        val tvPath = floatingView.findViewById<TextView>(R.id.tv_explorer_path)
        val btnUp = floatingView.findViewById<View>(R.id.btn_explorer_up)
        val btnSortType = floatingView.findViewById<Button>(R.id.btn_sort_type)
        val btnSortField = floatingView.findViewById<ImageView>(R.id.btn_sort_order)

        // Setup sort buttons
        btnSortType?.text = if (explorerSortByName) "Name" else "Date"
        btnSortField?.setImageResource(if (explorerSortAscending) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float)

        if (useScopedDir) {
            btnImported.text = "File Explorer"
        } else {
            btnImported.text = "Imported"
        }

        val fabAddBook = floatingView.findViewById<View>(R.id.fab_add_book)

        if (currentLibraryTab == "Recent") {
            btnRecent.setTextColor(android.graphics.Color.WHITE)
            btnImported.setTextColor(android.graphics.Color.GRAY)
            barExplorerTools?.visibility = View.GONE
            fabAddBook?.visibility = View.GONE
        } else {
            btnRecent.setTextColor(android.graphics.Color.GRAY)
            btnImported.setTextColor(android.graphics.Color.WHITE)
            if (useScopedDir) {
                barExplorerTools?.visibility = View.VISIBLE
                fabAddBook?.visibility = View.GONE
            } else {
                barExplorerTools?.visibility = View.GONE
                fabAddBook?.visibility = View.VISIBLE
            }
        }

        if (currentLibraryTab == "Imported" && useScopedDir) {
             serviceScope.launch(Dispatchers.IO) {
                 if (rootExplorerDir == null) {
                     rootExplorerDir = android.os.Environment.getExternalStorageDirectory()
                     explorerStack.clear()
                     if (rootExplorerDir != null) explorerStack.add(rootExplorerDir!!)
                 }
                 currentExplorerDir = explorerStack.lastOrNull()
                 
                 val files = currentExplorerDir?.listFiles()?.toList() ?: emptyList()
                 val filteredFiles = files.filter { it.isDirectory || it.name.endsWith(".epub", true) || it.name.endsWith(".txt", true) }.filter {
                     librarySearchQuery.isEmpty() || it.name.contains(librarySearchQuery, true)
                 }

                 val sortedFiles = filteredFiles.sortedWith(Comparator { a, b ->
                     if (a.isDirectory && !b.isDirectory) return@Comparator -1
                     if (!a.isDirectory && b.isDirectory) return@Comparator 1
                     
                     if (explorerSortByName) {
                         val nameA = a.name ?: ""
                         val nameB = b.name ?: ""
                         if (explorerSortAscending) nameA.compareTo(nameB, ignoreCase = true) else nameB.compareTo(nameA, ignoreCase = true)
                     } else {
                         val dateA = a.lastModified()
                         val dateB = b.lastModified()
                         if (explorerSortAscending) dateA.compareTo(dateB) else dateB.compareTo(dateA)
                     }
                 })

                 withContext(Dispatchers.Main) {
                     tvPath?.text = currentExplorerDir?.name ?: "Explorer"
                     btnUp?.visibility = if (currentExplorerDir?.absolutePath == rootExplorerDir?.absolutePath) View.GONE else View.VISIBLE
                     
                     listLibrary?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@FloatingReaderService)
                     listLibrary?.adapter = FileAdapter(
        sortedFiles,
        { file -> 
            explorerStack.add(file)
            saveLibraryState()
            loadLibraryBooks()
        },
        { file ->
            showToast("Importing ${file.name}...")
            serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val repo = com.example.data.LibraryRepository(this@FloatingReaderService)
                val book = repo.importBook(android.net.Uri.fromFile(file))
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (book != null) {
                        loadBook(book.id)
                        hideOverlays()
                    } else {
                        showToast("Failed to import")
                    }
                }
            }
        },
        { file -> showExplorerContextMenu(file) },
        { file -> loadEpubCover(file) },
        serviceScope
    )
                 }
             }
             return
        }

        serviceScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@FloatingReaderService)
            val books = if (currentLibraryTab == "Recent") {
                db.epubDao().getAllBooks()
            } else {
                db.epubDao().getAllBooksByAddedDesc()
            }.filter {
                librarySearchQuery.isEmpty() || it.title.contains(librarySearchQuery, true) || it.filePath.contains(librarySearchQuery, true)
            }
            withContext(Dispatchers.Main) {
                listLibrary?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@FloatingReaderService)
                listLibrary?.adapter = LibraryAdapter(
        books,
        { book ->
            if (book.isParsed) {
                loadBook(book.id)
                hideOverlays()
            } else {
                showToast("Book is still parsing...")
            }
        },
        { book ->
            serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val db = com.example.data.AppDatabase.getDatabase(this@FloatingReaderService)
                db.epubDao().deleteBook(book)
                // Just reload the library entirely
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    loadLibraryBooks()
                }
            }
            showToast("Deleted ${book.title}")
        }
    )
            }
        }
    }

    private val explorerStack = mutableListOf<java.io.File>()

    private fun inflateLibraryIfNeeded() {
        if (overlayLibrary == null) {
            val stub = floatingView.findViewById<android.view.ViewStub>(R.id.stub_overlay_library)
            if (stub != null) {
                overlayLibrary = stub.inflate()
                listLibrary = floatingView.findViewById(R.id.list_library)
                
                floatingView.findViewById<View>(R.id.fab_add_book)?.setOnClickListener {
                    val intent = Intent(this@FloatingReaderService, com.example.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("PICK_EPUB", true)
                    }
                    try { startActivity(intent) } catch (e: Exception) { AppLogger.d("Service", "Failed to start library import: ${e.message}") }
                }
                
                floatingView.findViewById<View>(R.id.fab_continue)?.setOnClickListener {
                    val lastBook = prefs.getInt("last_book_id", -1)
                    if (lastBook != -1) {
                        loadBook(lastBook)
                        hideOverlays()
                    } else {
                        showToast("No book read yet")
                    }
                }
                
                val etSearch = floatingView.findViewById<EditText>(R.id.et_search_library)
                floatingView.findViewById<View>(R.id.fab_search)?.setOnClickListener {
                    if (etSearch?.visibility == View.VISIBLE) {
                        etSearch.visibility = View.GONE
                        librarySearchQuery = ""
                        loadLibraryBooks()
                        updateWindowFocusAbility()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
                    } else {
                        etSearch?.visibility = View.VISIBLE
                        updateWindowFocusAbility()
                        etSearch?.requestFocus()
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.showSoftInput(etSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                etSearch?.addTextChangedListener(object: android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        librarySearchQuery = s?.toString() ?: ""
                        loadLibraryBooks()
                    }
                })

                val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                        if (e1 != null && e2 != null && overlayLibrary?.visibility == android.view.View.VISIBLE) {
                            val dx = e2.x - e1.x
                            val dy = e2.y - e1.y
                            if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 100 && Math.abs(velocityX) > 100) {
                                if (dx > 0) {
                                    if (currentLibraryTab == "Imported") {
                                        currentLibraryTab = "Recent"
                                        loadLibraryBooks()
                                        return true
                                    }
                                } else {
                                    if (currentLibraryTab == "Recent") {
                                        currentLibraryTab = "Imported"
                                        loadLibraryBooks()
                                        return true
                                    }
                                }
                            }
                        }
                        return false
                    }
                })

                listLibrary?.setOnTouchListener { _, event ->
                    gestureDetector.onTouchEvent(event)
                    false
                }

                floatingView.findViewById<android.view.View>(R.id.library_header)?.setOnTouchListener(createLongPressDragListener())

                // Settings listeners
                floatingView.findViewById<android.view.View>(R.id.btn_library_settings)?.setOnClickListener {
                    val intent = Intent(this@FloatingReaderService, com.example.SettingsActivity::class.java).apply {
                        putExtra("start_route", "reader")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    try { startActivity(intent) } catch (e: Exception) { AppLogger.d("Service", "Failed to start settings: ${e.message}") }
                    setFolded(true)
                }
                floatingView.findViewById<android.view.View>(R.id.btn_library_tracker)?.setOnClickListener {
                    val intent = android.content.Intent(this, com.example.TrackerActivity::class.java).apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    try { startActivity(intent) } catch (e: Exception) { AppLogger.d("Service", "Failed to start tracker: ${e.message}") }
                    setFolded(true)
                }

                floatingView.findViewById<android.widget.Button>(R.id.btn_tab_recent)?.setOnClickListener {
                    currentLibraryTab = "Recent"
                    saveLibraryState()
                    loadLibraryBooks()
                }
                
                floatingView.findViewById<android.widget.Button>(R.id.btn_tab_imported)?.setOnClickListener {
                    currentLibraryTab = "Imported"
                    saveLibraryState()
                    loadLibraryBooks()
                }

                floatingView.findViewById<android.view.View>(R.id.btn_explorer_up)?.setOnClickListener {
                    if (explorerStack.size > 1) {
                        explorerStack.removeLast()
                        saveLibraryState()
                        loadLibraryBooks()
                    }
                }
                
                floatingView.findViewById<android.view.View>(R.id.btn_sort_type)?.setOnClickListener {
                    explorerSortByName = !explorerSortByName
                    saveLibraryState()
                    loadLibraryBooks()
                }
                
                floatingView.findViewById<android.view.View>(R.id.btn_sort_order)?.setOnClickListener {
                    explorerSortAscending = !explorerSortAscending
                    saveLibraryState()
                    loadLibraryBooks()
                }
                
                val bgColor = prefs.getInt("bg_color", android.graphics.Color.parseColor("#222222"))
                overlayLibrary?.setBackgroundColor(bgColor)
            }
        }
    }

    private fun openLibraryView() {
        inflateLibraryIfNeeded()
        hideOverlays()
        overlayLibrary?.visibility = View.VISIBLE
        scrollView.visibility = View.GONE
        toolbarContainer.visibility = View.GONE
        tvWindowTitle.text = "Library"
        syncWindowStates()
        
        loadLibraryBooks()
    }

    private fun inflateChaptersIfNeeded() {
        if (overlayChapters == null) {
            val stub = floatingView.findViewById<android.view.ViewStub>(R.id.stub_overlay_chapters)
            if (stub != null) {
                overlayChapters = stub.inflate()
                listChapters = floatingView.findViewById(R.id.list_chapters)
                val bgColor = prefs.getInt("bg_color", android.graphics.Color.parseColor("#222222"))
                overlayChapters?.setBackgroundColor(bgColor)
            }
        }
    }

    private fun openChaptersView() {
        inflateChaptersIfNeeded()
        hideOverlays()
        overlayChapters?.visibility = View.VISIBLE
        // Remove scrollView.visibility = View.GONE so chapters side panel is over the book text
        toolbarContainer.visibility = View.GONE
        tvWindowTitle.text = "Chapters"

        currentBook?.totalChapters?.let { count ->
            listChapters?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            listChapters?.adapter = ChapterAdapter(
        count,
        currentChapterIndex,
        { pos ->
            saveCurrentPosition()
            currentChapterIndex = pos
            currentBook?.let { book ->
                currentBook = book.copy(lastReadChapter = pos, lastReadScrollY = 0)
            }
            loadChapterText()
            hideOverlays()
        }
    )
            listChapters?.scrollToPosition(currentChapterIndex)
        }
        syncWindowStates()
    }



    private fun openNotesView() {
        cameFromLibrary = (overlayLibrary?.visibility == View.VISIBLE)
        hideOverlays()
        inflateNotesIfNeeded()
        overlayNotes?.visibility = View.VISIBLE
        toolbarContainer.visibility = View.GONE
        loadNotes()
        syncWindowStates()
    }

    private fun inflateBookmarksIfNeeded() {
        if (overlayBookmarks == null) {
            val stub = floatingView.findViewById<android.view.ViewStub>(R.id.stub_overlay_bookmarks)
            if (stub != null) {
                overlayBookmarks = stub.inflate()
                listBookmarks = floatingView.findViewById(R.id.list_bookmarks)
                
                floatingView.findViewById<android.view.View>(R.id.bookmarks_header)?.setOnTouchListener(createLongPressDragListener())
                
                floatingView.findViewById<android.view.View>(R.id.btn_bookmarks_back)?.setOnClickListener {
                    overlayBookmarks?.visibility = View.GONE
                    syncWindowStates()
                }
                
                floatingView.findViewById<android.view.View>(R.id.btn_add_bookmark)?.setOnClickListener {
                    val offset = tvContent.layout?.getLineStart(tvContent.layout?.getLineForVertical(scrollView.scrollY) ?: 0) ?: 0
                    val content = tvContent.text.toString().substring(offset).trim()
                    val words = if (content.length > 50) content.substring(0, 50).replace("\n", " ").trim() + "..." else content
                    var percent = 0
                    val totalChapters = currentBook?.totalChapters ?: 0
                    if (totalChapters > 0) {
                        val scrollY = scrollView.scrollY
                        val maxScrollY = tvContent.height - scrollView.height
                        val currentChapterPercent = if (maxScrollY > 0) (scrollY.toFloat() / maxScrollY) else 0f
                        percent = (((currentChapterIndex + currentChapterPercent) * 100) / totalChapters).toInt()
                    }
                    bookmarksList.add(0, BookmarkItem(words, currentChapterIndex, percent))
                    showToast("Bookmark added at Chapter ${currentChapterIndex + 1}")
                    listBookmarks?.adapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private fun inflateSearchIfNeeded() {
        if (overlaySearch == null) {
            val stubSearch = floatingView.findViewById<android.view.ViewStub>(R.id.stub_overlay_search)
            if (stubSearch != null) {
                overlaySearch = stubSearch.inflate()
            }
        }
        if (overlaySearchResults == null) {
            val stubResults = floatingView.findViewById<android.view.ViewStub>(R.id.stub_overlay_search_results)
            if (stubResults != null) {
                overlaySearchResults = stubResults.inflate()
            }
        }
        
        // Always attempt to re-bind listeners in case they were lost or just to ensure they exist
        val etSearch = floatingView.findViewById<EditText>(R.id.et_search)
        
        floatingView.findViewById<View>(R.id.btn_close_search)?.setOnClickListener {
            overlaySearch?.visibility = View.GONE
            overlaySearchResults?.visibility = View.GONE
            syncWindowStates()
        }
        
        floatingView.findViewById<View>(R.id.btn_do_search)?.setOnClickListener {
            val query = etSearch?.text?.toString() ?: ""
            if (query.isNotEmpty() && chapterContent.contains(query, ignoreCase = true)) {
                val index = chapterContent.indexOf(query, ignoreCase = true)
                if (index >= 0) {
                    val searchLayout = tvContent.layout
                    if (searchLayout != null) {
                        val line = searchLayout.getLineForOffset(index)
                        val y = searchLayout.getLineTop(line)
                        scrollView.smoothScrollTo(0, y)
                    }
                }
            } else {
                showToast("Not found in chapter")
            }
        }
        
        floatingView.findViewById<View>(R.id.btn_close_search_results)?.setOnClickListener {
            overlaySearchResults?.visibility = View.GONE
            searchJob?.cancel()
            syncWindowStates()
        }

        floatingView.findViewById<View>(R.id.btn_do_search_full)?.setOnClickListener {
            val query = etSearch?.text?.toString() ?: ""
            if (query.isNotEmpty()) {
                performFullBookSearch(query)
            }
        }
    }

    private fun openBookmarksView() {
        inflateBookmarksIfNeeded()
        hideOverlays()
        overlayBookmarks?.visibility = View.VISIBLE
        toolbarContainer.visibility = View.GONE
        listBookmarks?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        listBookmarks?.adapter = BookmarkAdapter(
        bookmarksList,
        { chapter, offset -> loadAndJumpToOffset(chapter, offset) },
        { floatingView.findViewById<android.view.View>(com.example.R.id.overlay_bookmarks)?.visibility = android.view.View.GONE }
    )
        syncWindowStates()
    }

    private fun setupListeners() {
        val topDragBar = floatingView.findViewById<View>(R.id.top_drag_bar)
        val resizeHandle = floatingView.findViewById<View>(R.id.resize_handle)
        
        // Tap outside to fold removed

        // Long press movement for bubble and top bar
        val dragListener = createLongPressDragListener()
        bubbleIcon.setOnTouchListener(dragListener)
        topDragBar.setOnTouchListener(dragListener)

        // Make overlay headers draggable too as they replace topDragBar when overlays are shown
        floatingView.findViewById<View>(R.id.notes_header)?.setOnTouchListener(dragListener)
        floatingView.findViewById<View>(R.id.bookmarks_header)?.setOnTouchListener(dragListener)

        // Click listener for top notes button in reader
        floatingView.findViewById<View>(R.id.btn_top_notes)?.setOnClickListener {
            openNotesView()
        }

        bubbleIcon.setOnClickListener { setFolded(false) }

        floatingView.findViewById<View>(R.id.btn_exit_bottom)?.setOnClickListener {
            saveCurrentPosition()
            com.example.LogKeeper.writeLog("eBookReader", "Closing reader")
            setFolded(true)
        }

        // Tap content to toggle Moonreader toolbar handled in touch listener now

        // Resize bottom right
        resizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.width
                    initialY = layoutParams.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newWidth = initialX + (event.rawX - initialTouchX).toInt()
                    val newHeight = initialY + (event.rawY - initialTouchY).toInt()
                    layoutParams.width = max(400, newWidth)
                    layoutParams.height = max(600, newHeight)
                    savedWindowWidth = layoutParams.width
                    savedWindowHeight = layoutParams.height
                    prefs.edit()
                        .putInt("win_w", savedWindowWidth)
                        .putInt("win_h", savedWindowHeight)
                        .apply()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                else -> false
            }
        }
        
        // Toolbar Buttons
        floatingView.findViewById<View>(R.id.btn_prev).setOnClickListener { navigateChapter(-1) }
        floatingView.findViewById<View>(R.id.btn_next).setOnClickListener { navigateChapter(1) }
        floatingView.findViewById<View>(R.id.btn_chapters).setOnClickListener {
            openChaptersView()
        }
        floatingView.findViewById<View>(R.id.btn_minimize_bottom)?.setOnClickListener {
            setFolded(true)
        }
        floatingView.findViewById<View>(R.id.btn_exit_bottom)?.setOnClickListener {
            saveCurrentPosition()
            com.example.LogKeeper.writeLog("eBookReader", "Closing reader window")
            setFolded(true)
        }

        floatingView.findViewById<View>(R.id.btn_copy_text)?.setOnClickListener {
            if (tvContent.hasSelection()) {
                val start = tvContent.selectionStart
                val end = tvContent.selectionEnd
                if (start != -1 && end != -1) {
                    val selectedText = tvContent.text.substring(Math.min(start, end), Math.max(start, end))
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Copied Text", selectedText)
                    clipboard.setPrimaryClip(clip)
                    showToast("Text Copied")
                }
            }
            floatingView.findViewById<View>(R.id.custom_selection_menu)?.visibility = View.GONE
            // Clear selection
            (tvContent.text as? android.text.Spannable)?.let { android.text.Selection.removeSelection(it) }
        }

        floatingView.findViewById<View>(R.id.btn_share_text)?.setOnClickListener {
            if (tvContent.hasSelection()) {
                val start = tvContent.selectionStart
                val end = tvContent.selectionEnd
                if (start != -1 && end != -1) {
                    val selectedText = tvContent.text.substring(Math.min(start, end), Math.max(start, end))
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, selectedText)
                        type = "text/plain"
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Share Text")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(chooser)
                }
            }
            floatingView.findViewById<View>(R.id.custom_selection_menu)?.visibility = View.GONE
            // Clear selection
            (tvContent.text as? android.text.Spannable)?.let { android.text.Selection.removeSelection(it) }
        }

        floatingView.findViewById<View>(R.id.btn_close_selection)?.setOnClickListener {
            floatingView.findViewById<View>(R.id.custom_selection_menu)?.visibility = View.GONE
            // Clear selection
            (tvContent.text as? android.text.Spannable)?.let { android.text.Selection.removeSelection(it) }
        }
        
        floatingView.findViewById<View>(R.id.btn_bookmarks)?.setOnClickListener {
            openBookmarksView()
        }
        floatingView.findViewById<View>(R.id.btn_library).setOnClickListener {
            saveCurrentPosition()
            openLibraryView()
        }
        floatingView.findViewById<View>(R.id.btn_auto_scroll).setOnClickListener {
            isAutoScrolling = !isAutoScrolling
            if (isAutoScrolling) {
                showToast("Auto-scroll ON")
                scrollHandler.post(scrollRunnable)
            } else {
                showToast("Auto-scroll OFF")
                scrollHandler.removeCallbacks(scrollRunnable)
            }
        }
        floatingView.findViewById<View>(R.id.btn_search).setOnClickListener {
            inflateSearchIfNeeded()
            val isSearchVisible = overlaySearch?.visibility == View.VISIBLE
            hideOverlays()
            overlaySearch?.visibility = if (isSearchVisible) View.GONE else View.VISIBLE
            toolbarContainer.visibility = View.GONE
            syncWindowStates()
        }
        
        val seekProgress = floatingView.findViewById<SeekBar>(R.id.seek_progress)
        seekProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentBook?.let { book ->
                        val targetChapter = (progress * max(1, book.totalChapters - 1)) / 100
                        navigateChapter(targetChapter - currentChapterIndex)
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        floatingView.findViewById<View>(R.id.btn_settings).setOnClickListener {
            val intent = Intent(this@FloatingReaderService, com.example.SettingsActivity::class.java).apply {
                putExtra("start_route", "reader")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try { startActivity(intent) } catch (e: Exception) { AppLogger.d("Service", "Failed to start reader settings: ${e.message}") }
            setFolded(true)
        }
        
        var lastTopBarTapTime = 0L
        floatingView.findViewById<View>(R.id.top_drag_bar).setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTopBarTapTime < 300) {
                toggleFullScreen()
                lastTopBarTapTime = 0L
            } else {
                lastTopBarTapTime = now
                if (overlayLibrary?.visibility == View.VISIBLE || overlayChapters?.visibility == View.VISIBLE) {
                    hideOverlays()
                    currentBook?.let { 
                        val displayTitle = it.title.replace(Regex("(?i)\\.epub$"), "")
                        tvWindowTitle.text = "$displayTitle (Ch ${currentChapterIndex + 1}/${it.totalChapters})"
                    }
                }
            }
        }
        
        floatingView.findViewById<View>(R.id.btn_close_fullscreen)?.setOnClickListener {
            toggleFullScreen()
        }
        
        btnTts.setOnClickListener {
            toggleTts()
        }

        // Track scrolling for progress saving
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                scrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                    lastKnownScrollY = scrollY
                    if (prefs.getBoolean("continuous_save", false)) {
                        scrollHandler.removeCallbacks(saveScrollRunnable)
                        scrollHandler.postDelayed(saveScrollRunnable, 1500)
                    }
                }
            }
        }

        private val saveScrollRunnable = Runnable {
            saveCurrentPosition()
        }

    private var lastKnownScrollY = 0

    private fun createLongPressDragListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private val handler = Handler(Looper.getMainLooper())
            private var isLongPressed = false
            private var downX = 0f
            private var downY = 0f
            private val longPressRunnable = Runnable { isLongPressed = true }

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        downX = event.rawX
                        downY = event.rawY
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isLongPressed = false
                        handler.postDelayed(longPressRunnable, 300)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isLongPressed) {
                            if (Math.abs(event.rawX - downX) > 20 || Math.abs(event.rawY - downY) > 20) {
                                handler.removeCallbacks(longPressRunnable)
                            }
                        } else {
                            layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                            layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                            if (isFolded) {
                                foldedX = layoutParams.x
                                foldedY = layoutParams.y
                                prefs.edit()
                                    .putInt("fold_x", foldedX)
                                    .putInt("fold_y", foldedY)
                                    .apply()
                            } else {
                                savedWindowX = layoutParams.x
                                savedWindowY = layoutParams.y
                                prefs.edit()
                                    .putInt("win_x", savedWindowX)
                                    .putInt("win_y", savedWindowY)
                                    .apply()
                            }
                            windowManager.updateViewLayout(floatingView, layoutParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        if (!isLongPressed) {
                            if (Math.abs(event.rawX - downX) < 20 && Math.abs(event.rawY - downY) < 20) {
                                view.performClick()
                            }
                        }
                        isLongPressed = false
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun loadBook(bookId: Int) {
        prefs.edit().putInt("last_book_id", bookId).apply()
        serviceScope.launch {
            val db = AppDatabase.getDatabase(this@FloatingReaderService)
            val fetchedBook = db.epubDao().getBookById(bookId)
            var book = fetchedBook?.copy(lastOpenedTimestamp = System.currentTimeMillis())
            if (book != null) {
                com.example.LogKeeper.writeLog("eBookReader", "Opened book: ${book.title}")
                // If there's a TrackerBook from Moon+ Reader or track goal, sync reading progress to EPUB
                val trackerBook = db.trackerDao().getAllBooks().firstOrNull { it.title.equals(book.title, true) }
                if (trackerBook != null && trackerBook.readChapters > book.lastReadChapter) {
                    book = book.copy(lastReadChapter = trackerBook.readChapters)
                }
                db.epubDao().updateBook(book)
                
                // Trim library to 10 most recent books
                val allBooks = db.epubDao().getAllBooks()
                if (allBooks.size > 10) {
                    for (i in 10 until allBooks.size) {
                        val bookToDelete = allBooks[i]
                        db.epubDao().deleteBook(bookToDelete)
                        File(filesDir, "book_${bookToDelete.id}").deleteRecursively()
                        val origFile = File(bookToDelete.filePath)
                        if (origFile.exists()) origFile.delete()
                    }
                }
                
                currentBook = book
                
                var targetChapter = book.lastReadChapter
                val totalChapters = book.totalChapters
                if (targetChapter >= totalChapters) {
                    targetChapter = totalChapters - 1
                }
                if (targetChapter < 0) {
                    targetChapter = 0
                }
                currentChapterIndex = targetChapter
                loadChapterText()
            }
        }
    }

    private fun loadChapterText(scrollToBottom: Boolean = false) {
        val book = currentBook ?: return
        serviceScope.launch(Dispatchers.IO) {
            val bookDir = File(filesDir, "book_${book.id}")
            val chapterFile = File(bookDir, "chapter_$currentChapterIndex.txt")
            if (chapterFile.exists()) {
                val text = chapterFile.readText()
                withContext(Dispatchers.Main) {
                    com.example.LogKeeper.writeLog("eBookReader", "Reading Chapter ${currentChapterIndex + 1}")
                    chapterContent = text
                    renderChapter(book.lastReadScrollY, scrollToBottom)
                }
            } else {
                withContext(Dispatchers.Main) {
                    chapterContent = "Chapter content not found."
                    renderChapter(0, scrollToBottom)
                }
            }
        }
    }

    private fun renderChapter(scrollY: Int, scrollToBottom: Boolean = false) {
        val book = currentBook ?: return
        val displayTitle = book.title.replace(Regex("(?i)\\.epub$"), "")
        tvWindowTitle.text = "$displayTitle (Ch ${currentChapterIndex + 1}/${book.totalChapters})"
        
        tvContent.text = chapterContent
        val percent = if (book.totalChapters > 1) {
            (currentChapterIndex * 100) / (book.totalChapters - 1)
        } else {
            100
        }
        tvProgress.text = "$percent%"
        
        floatingView.findViewById<SeekBar>(R.id.seek_progress)?.progress = percent
        
        floatingView.findViewById<ProgressBar>(R.id.loading_spinner)?.visibility = View.GONE
        tvContent.visibility = View.VISIBLE
        
        scrollView.post {
            if (scrollToBottom) {
                // Scroll to bottom
                val maxScroll = Math.max(0, scrollView.getChildAt(0).height - scrollView.height)
                scrollView.scrollTo(0, maxScroll)
            } else {
                scrollView.scrollTo(0, scrollY)
            }
        }
    }

    private fun navigateChapter(offset: Int, scrollToBottom: Boolean = false) {
        currentBook?.let { book ->
            val newIndex = currentChapterIndex + offset
            if (newIndex in 0 until book.totalChapters) {
                saveCurrentPosition() // save before swap
                
                serviceScope.launch(Dispatchers.Main) {
                    val spinner = floatingView.findViewById<ProgressBar>(R.id.loading_spinner)
                    spinner?.visibility = View.VISIBLE
                    tvContent.visibility = View.INVISIBLE
                    delay(1000) // 1 second spinner as requested
                    
                    currentChapterIndex = newIndex
                    // aggressively update db memory
                    val updated = book.copy(lastReadChapter = newIndex, lastReadScrollY = 0)
                    currentBook = updated
                    loadChapterText(scrollToBottom)
                }
            }
        }
    }

    private var autoSaveJob: Job? = null
    private var searchJob: Job? = null

    private fun performFullBookSearch(query: String) {
        val book = currentBook ?: return
        
        inflateSearchIfNeeded()
        val overlayResults = overlaySearchResults
        val tvStatus = floatingView.findViewById<TextView>(R.id.tv_search_status)
        val llResults = floatingView.findViewById<LinearLayout>(R.id.ll_search_results)
        
        overlayResults?.visibility = View.VISIBLE
        llResults?.removeAllViews()
        tvStatus?.text = "Searching..."
        
        searchJob?.cancel()
        searchJob = serviceScope.launch(Dispatchers.IO) {
            val bookDir = File(filesDir, "book_${book.id}")
            var matchCount = 0
            
            for (i in 0 until book.totalChapters) {
                if (!isActive) break // Canceled
                
                withContext(Dispatchers.Main) {
                    tvStatus?.text = "Searching chapter ${i+1}/${book.totalChapters}..."
                }
                
                val chapterFile = File(bookDir, "chapter_$i.txt")
                if (chapterFile.exists()) {
                    val text = chapterFile.readText()
                    var startIndex = 0
                    while (startIndex < text.length) {
                        val index = text.indexOf(query, startIndex, ignoreCase = true)
                        if (index == -1) break
                        
                        matchCount++
                        if (matchCount > 500) break // Cap results
                        
                        val snippetStart = max(0, index - 40)
                        val snippetEnd = minOf(text.length, index + query.length + 40)
                        val snippetStr = text.substring(snippetStart, snippetEnd)
                            .replace("\n", " ").trim()
                            
                        // Show result on main thread immediately
                        withContext(Dispatchers.Main) {
                            val tvResult = TextView(this@FloatingReaderService).apply {
                                setPadding(24, 24, 24, 24)
                                setTextColor(Color.LTGRAY)
                                textSize = 14f
                                
                                val spannable = SpannableString("Ch ${i+1}: ...$snippetStr...")
                                // Highlight query string inside the snippet
                                val colorSpanStart = spannable.indexOf(query, ignoreCase = true)
                                if (colorSpanStart >= 0) {
                                    val colorSpanEnd = colorSpanStart + query.length
                                    spannable.setSpan(BackgroundColorSpan(Color.parseColor("#880099CC")), colorSpanStart, colorSpanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                }
                                setText(spannable)
                                
                                setBackgroundResource(android.R.drawable.list_selector_background)
                                isClickable = true
                                isFocusable = true
                                setOnClickListener {
                                    overlayResults?.visibility = View.GONE
                                    overlaySearch?.visibility = View.GONE
                                    loadAndJumpToOffset(i, index)
                                }
                            }
                            llResults.addView(tvResult)
                            
                            val divider = View(this@FloatingReaderService).apply {
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                                setBackgroundColor(Color.DKGRAY)
                            }
                            llResults.addView(divider)
                        }
                        
                        startIndex = index + query.length
                    }
                }
                
                if (matchCount > 500) break
            }
            
            withContext(Dispatchers.Main) {
                if (isActive) {
                    if (matchCount == 0) {
                        tvStatus?.text = "No results found."
                    } else {
                        tvStatus?.text = "Found $matchCount results."
                    }
                }
            }
        }
    }

    private fun loadAndJumpToOffset(targetChapterIndex: Int, textOffset: Int) {
        val book = currentBook ?: return
        serviceScope.launch(Dispatchers.IO) {
            val bookDir = File(filesDir, "book_${book.id}")
            val chapterFile = File(bookDir, "chapter_$targetChapterIndex.txt")
            if (chapterFile.exists()) {
                val text = chapterFile.readText()
                withContext(Dispatchers.Main) {
                    currentChapterIndex = targetChapterIndex
                    chapterContent = text
                    val displayTitle = book.title.replace(Regex("(?i)\\.epub$"), "")
                    tvWindowTitle.text = "$displayTitle (Ch ${currentChapterIndex + 1}/${book.totalChapters})"
                    tvContent.text = chapterContent
                    
                    val percent = if (book.totalChapters > 1) {
                        (currentChapterIndex * 100) / (book.totalChapters - 1)
                    } else 100
                    tvProgress.text = "$percent%"
                    floatingView.findViewById<SeekBar>(R.id.seek_progress)?.progress = percent
                    
                    // Post to wait for layout
                    scrollView.postDelayed({
                        val searchLayout = tvContent.layout
                        if (searchLayout != null) {
                            val line = searchLayout.getLineForOffset(textOffset)
                            val y = searchLayout.getLineTop(line)
                            scrollView.scrollTo(0, y)
                            saveCurrentPosition()
                        }
                    }, 50)
                }
            }
        }
    }

    private fun startAutoSaveTimer() {
        autoSaveJob?.cancel()
        autoSaveJob = serviceScope.launch {
            while (true) {
                delay(15 * 60 * 1000L) // 15 minutes
                saveCurrentPosition()
            }
        }
    }

    private fun saveCurrentPosition() {
        currentBook?.let { book ->
            val updated = book.copy(lastReadChapter = currentChapterIndex, lastReadScrollY = lastKnownScrollY)
            serviceScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(this@FloatingReaderService)
                db.epubDao().updateBook(updated)
                
                val trackerBooks = db.trackerDao().getAllBooks()
                val existingTracker = trackerBooks.firstOrNull { it.title == book.title }
                val totalCh = book.totalChapters
                if (existingTracker != null) {
                    if (existingTracker.readChapters != currentChapterIndex || existingTracker.totalChapters < totalCh) {
                        val newTracker = existingTracker.copy(
                            readChapters = Math.max(existingTracker.readChapters, currentChapterIndex),
                            totalChapters = Math.max(existingTracker.totalChapters, totalCh),
                            lastUpdatedTimestamp = System.currentTimeMillis()
                        )
                        db.trackerDao().updateBook(newTracker)
                    }
                } else {
                    val newTracker = com.example.data.TrackerBook(
                        title = book.title,
                        author = "",
                        totalChapters = totalCh,
                        readChapters = currentChapterIndex,
                        addedTimestamp = System.currentTimeMillis(),
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                    db.trackerDao().insertBook(newTracker)
                }
            }
        }
    }

    private fun toggleTts() {
        if (tts == null) {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    isTtsReady = true
                    executeTtsToggle()
                } else {
                    showToast("TTS failed to initialize")
                }
            }
            return
        }
        executeTtsToggle()
    }

    private fun executeTtsToggle() {
        if (!isTtsReady) {
            showToast("TTS not ready")
            return
        }
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
            btnTts.setImageResource(android.R.drawable.ic_media_play)
        } else {
            val offset = tvContent.layout?.getLineStart(tvContent.layout?.getLineForVertical(scrollView.scrollY) ?: 0) ?: 0
            val textToSpeak = chapterContent.substring(offset)
            val chunks = textToSpeak.chunked(3000)
            for (chunk in chunks) {
                tts?.speak(chunk, TextToSpeech.QUEUE_ADD, null, null)
            }
            isSpeaking = true
            btnTts.setImageResource(android.R.drawable.ic_media_pause)
        }
    }



    

    private fun getCoverCacheDir(): java.io.File {
        val root = android.os.Environment.getExternalStorageDirectory()
        val cacheDir = java.io.File(root, "Books/VianReader/.covers")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    private fun loadEpubCover(file: java.io.File): android.graphics.Bitmap? {
        val cacheDir = getCoverCacheDir()
        val cacheFile = java.io.File(cacheDir, "${file.absolutePath.hashCode()}.jpg")
        
        if (cacheFile.exists()) {
            try {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                return android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath, opts)
            } catch (e: Exception) {}
        }
        
        try {
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                val coverEntry = entries.firstOrNull { 
                    it.name.contains("cover", true) && 
                    (it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true) || it.name.endsWith(".jpeg", true)) 
                } ?: entries.firstOrNull { 
                    it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true) || it.name.endsWith(".jpeg", true) 
                }
                
                if (coverEntry != null) {
                    val bytes = zip.getInputStream(coverEntry).readBytes()
                    
                    try {
                        java.io.FileOutputStream(cacheFile).use { fos ->
                            fos.write(bytes)
                        }
                    } catch(e: Exception) {}
                    
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                }
            }
        } catch (e: Exception) {}
        return null
    }

    private fun showExplorerContextMenu(file: java.io.File) {
        val options = arrayOf("Properties", "Rename", "Delete")
        val builder = android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert))
        builder.setTitle(file.name)
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> {
                    // Properties
                    val sizeBytes = file.length()
                    val sizeMB = sizeBytes / (1024f * 1024f)
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
                    val props = "Name: ${file.name}\nSize: ${String.format("%.2f MB", sizeMB)}\nModified: $date\nType: ${if(file.isDirectory) "Folder" else "File"}"
                    android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert))
                        .setTitle("Properties")
                        .setMessage(props)
                        .setPositiveButton("OK", null)
                        .run {
                            val d = create()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                d.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                            } else {
                                d.window?.setType(android.view.WindowManager.LayoutParams.TYPE_PHONE)
                            }
                            d.show()
                        }
                }
                1 -> {
                    // Rename
                    val input = android.widget.EditText(this)
                    input.setText(file.name)
                    android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert))
                        .setTitle("Rename")
                        .setView(input)
                        .setPositiveButton("Rename") { _, _ ->
                            val newName = input.text.toString()
                            if (newName.isNotBlank()) {
                                val newFile = java.io.File(file.parent, newName)
                                if (file.renameTo(newFile)) {
                                    showToast("Renamed successful")
                                    loadLibraryBooks()
                                } else {
                                    showToast("Rename failed")
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .run {
                            val d = create()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                d.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                            } else {
                                d.window?.setType(android.view.WindowManager.LayoutParams.TYPE_PHONE)
                            }
                            d.show()
                        }
                }
                2 -> {
                    // Delete
                    android.app.AlertDialog.Builder(android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert))
                        .setTitle("Delete")
                        .setMessage("Are you sure you want to delete ${file.name}?")
                        .setPositiveButton("Delete") { _, _ ->
                            if (file.delete()) {
                                showToast("Deleted")
                                loadLibraryBooks()
                            } else {
                                showToast("Delete failed")
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .run {
                            val d = create()
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                d.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                            } else {
                                d.window?.setType(android.view.WindowManager.LayoutParams.TYPE_PHONE)
                            }
                            d.show()
                        }
                }
            }
        }
        val dialog = builder.create()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            dialog.window?.setType(android.view.WindowManager.LayoutParams.TYPE_PHONE)
        }
        dialog.show()
    }

    

    // --- Quick Notes Implementation ---
    private lateinit var listNotes: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnNotesBack: View
    private lateinit var btnNotesAdd: View
    private lateinit var btnNotesDelete: View
    private lateinit var tvNotesTitle: android.widget.TextView
    private var notesAdapter: NotesAdapter? = null
    
    private val selectedNotes = mutableSetOf<com.example.data.QuickNote>()
    private var notesList = listOf<com.example.data.QuickNote>()
    
    private fun inflateNotesIfNeeded() {
        if (overlayNotes == null) {
            val stub = floatingView.findViewById<android.view.ViewStub>(R.id.stub_overlay_notes)
            if (stub != null) {
                overlayNotes = stub.inflate()
                floatingView.findViewById<View>(R.id.notes_header)?.setOnTouchListener(createLongPressDragListener())
            }
        }
        
        // Always bind just to be sure
        listNotes = floatingView.findViewById(R.id.list_notes)
        btnNotesBack = floatingView.findViewById(R.id.btn_notes_back)
        btnNotesAdd = floatingView.findViewById(R.id.btn_notes_add)
        btnNotesDelete = floatingView.findViewById(R.id.btn_notes_delete)
        tvNotesTitle = floatingView.findViewById(R.id.tv_notes_title)
        
        listNotes.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        notesAdapter = NotesAdapter(
        notesList,
        selectedNotes,
        { updateNotesUi() },
        { note -> showNoteDialog(note) }
    )
        listNotes.adapter = notesAdapter
        
        val etSearchNotes = floatingView.findViewById<android.widget.EditText>(R.id.et_search_notes)
        floatingView.findViewById<View>(R.id.btn_notes_search)?.setOnClickListener {
            if (etSearchNotes?.visibility == View.VISIBLE) {
                etSearchNotes.visibility = View.GONE
                notesSearchQuery = ""
                loadNotes()
                updateWindowFocusAbility()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(etSearchNotes.windowToken, 0)
            } else {
                etSearchNotes?.visibility = View.VISIBLE
                updateWindowFocusAbility()
                etSearchNotes?.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etSearchNotes, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        
        etSearchNotes?.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                notesSearchQuery = s?.toString() ?: ""
                loadNotes()
            }
        })
        
        btnNotesBack.setOnClickListener {
            if (selectedNotes.isNotEmpty()) {
                selectedNotes.clear()
                updateNotesUi()
            } else {
                overlayNotes?.visibility = View.GONE
                if (cameFromLibrary) {
                    openLibraryView()
                } else {
                    scrollView.visibility = View.VISIBLE
                    toolbarContainer.visibility = View.VISIBLE
                }
                syncWindowStates()
            }
        }
        
        btnNotesAdd.setOnClickListener {
            showNoteDialog(null)
        }
        
        btnNotesDelete.setOnClickListener {
            if (selectedNotes.isNotEmpty()) {
                val toDelete = selectedNotes.toList()
                serviceScope.launch(Dispatchers.IO) {
                    val db = com.example.data.AppDatabase.getDatabase(this@FloatingReaderService)
                    db.quickNoteDao().deleteNotes(toDelete)
                    selectedNotes.clear()
                    loadNotes()
                    withContext(Dispatchers.Main) { updateNotesUi() }
                }
            }
        }
    }
    
    private fun updateNotesUi() {
        if (selectedNotes.isNotEmpty()) {
            tvNotesTitle.text = "${selectedNotes.size} Selected"
            btnNotesDelete.visibility = View.VISIBLE
            btnNotesAdd.visibility = View.GONE
        } else {
            tvNotesTitle.text = "Quick Notes"
            btnNotesDelete.visibility = View.GONE
            btnNotesAdd.visibility = View.VISIBLE
        }
        notesAdapter?.notifyDataSetChanged()
    }
    
    private fun loadNotes() {
        serviceScope.launch(Dispatchers.IO) {
            val db = com.example.data.AppDatabase.getDatabase(this@FloatingReaderService)
            db.quickNoteDao().getAllNotes().collect { notes ->
                notesList = notes.filter {
                    notesSearchQuery.isEmpty() || 
                    it.title.contains(notesSearchQuery, true) || 
                    it.text.contains(notesSearchQuery, true)
                }
                withContext(Dispatchers.Main) {
                    notesAdapter?.notifyDataSetChanged()
                }
            }
        }
    }
    
    private fun showNoteDialog(note: com.example.data.QuickNote?) {
        val editorContainer = overlayNotes?.findViewById<View>(R.id.note_editor_container) ?: return
        val etTitle = overlayNotes?.findViewById<android.widget.EditText>(R.id.et_note_title) ?: return
        val etText = overlayNotes?.findViewById<android.widget.EditText>(R.id.et_note_text) ?: return
        val btnSave = overlayNotes?.findViewById<android.widget.Button>(R.id.btn_note_save) ?: return
        val btnCancel = overlayNotes?.findViewById<android.widget.Button>(R.id.btn_note_cancel) ?: return
        
        if (note != null) {
            etTitle.setText(note.title)
            etText.setText(note.text)
        } else {
            etTitle.setText("")
            etText.setText("")
        }
        
        editorContainer.visibility = View.VISIBLE
        syncWindowStates()
        
        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val text = etText.text.toString().trim()
            if (title.isNotEmpty() || text.isNotEmpty()) {
                serviceScope.launch(Dispatchers.IO) {
                    val db = com.example.data.AppDatabase.getDatabase(this@FloatingReaderService)
                    if (note == null) {
                        db.quickNoteDao().insertNote(com.example.data.QuickNote(title = title, text = text))
                    } else {
                        db.quickNoteDao().updateNote(note.copy(title = title, text = text))
                    }
                }
            }
            editorContainer.visibility = View.GONE
            syncWindowStates()
        }
        
        btnCancel.setOnClickListener {
            editorContainer.visibility = View.GONE
            syncWindowStates()
        }
    }
    
    private var notesSearchQuery: String = ""

    

    
    
    private val bookmarksList = mutableListOf<BookmarkItem>()

    
        
    

    fun setFolded(folded: Boolean) {
        isFolded = folded
        if (folded) {
            // Save expanded position before folding
            savedWindowX = layoutParams.x
            savedWindowY = layoutParams.y
            prefs.edit()
                .putInt("win_x", savedWindowX)
                .putInt("win_y", savedWindowY)
                .apply()

            bubbleIcon.visibility = View.GONE
            windowContainer.visibility = View.GONE
            floatingView.visibility = View.GONE
            layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            
            // Restore bubble position
            layoutParams.x = foldedX
            layoutParams.y = foldedY
            
            isAutoScrolling = false // pause scroll
        } else {
            // Save bubble position before expanding
            foldedX = layoutParams.x
            foldedY = layoutParams.y
            prefs.edit()
                .putInt("fold_x", foldedX)
                .putInt("fold_y", foldedY)
                .apply()

            bubbleIcon.visibility = View.GONE
            windowContainer.visibility = View.VISIBLE
            floatingView.visibility = View.VISIBLE
            toolbarContainer.visibility = View.GONE
            
            val metrics = resources.displayMetrics
            val maxW = metrics.widthPixels
            val maxH = metrics.heightPixels
            
            layoutParams.width = Math.min(savedWindowWidth, maxW)
            layoutParams.height = Math.min(savedWindowHeight, maxH)
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            
            layoutParams.x = savedWindowX
            layoutParams.y = savedWindowY
            
            if (layoutParams.x + layoutParams.width > maxW) {
                layoutParams.x = maxW - layoutParams.width
            }
            if (layoutParams.x < 0) layoutParams.x = 0
            
            if (layoutParams.y + layoutParams.height > maxH) {
                layoutParams.y = maxH - layoutParams.height
            }
            if (layoutParams.y < 0) layoutParams.y = 0
        }
        windowManager.updateViewLayout(floatingView, layoutParams)
        updatePersistentNotification()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveCurrentPosition()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (this::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        }
        screenStateReceiver?.let { unregisterReceiver(it) }
        netSpeedManager?.stop()
        callRecorderManager?.stopListening()
        instance = null
        sidebarView?.detach()
        sidebarView = null
        sidebarPagesList.clear()
        appPickerOverlayView?.detach()
        appPickerOverlayView = null
        addElementOverlayView?.detach()
        addElementOverlayView = null
        appsPageViews.clear()
        if (::appsManager.isInitialized) {
            appsManager.destroy()
        }
        triggerHandleView?.detach()
        triggerHandleView = null
        readerHandleView?.detach()
        readerHandleView = null
        saveCurrentPosition()
        mediaSession?.isActive = false
        mediaSession?.release()
        tts?.stop()
        tts?.shutdown()
        scrollHandler.removeCallbacks(scrollRunnable)
        serviceScope.cancel()
        if (::windowManager.isInitialized && ::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
        serviceLifecycleOwner?.onPause()
        serviceLifecycleOwner?.onStop()
        serviceLifecycleOwner?.onDestroy()
        super.onDestroy()
    }
}
