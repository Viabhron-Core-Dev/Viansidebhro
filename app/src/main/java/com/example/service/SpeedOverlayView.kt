package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.example.util.AppLogger
import kotlin.math.max

@SuppressLint("ViewConstructor")
class SpeedOverlayView(
    context: Context,
    private val prefs: SharedPreferences,
    private val windowManager: WindowManager
) : FrameLayout(context) {

    private val speedText: TextView
    private var windowTokenObj: Any? = null
    private val layoutParams: WindowManager.LayoutParams
    
    init {
        speedText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(10, 0, 10, 0)
        }
        
        addView(speedText, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        
        layoutParams.gravity = Gravity.TOP or Gravity.START
        
        updateAppearance()
    }
    
    fun updateAppearance() {
        val fontSize = prefs.getInt("speed_indicator_font_size", 12).toFloat()
        speedText.textSize = fontSize
        
        val pos = prefs.getString("speed_indicator_position", "Left") ?: "Left"
        layoutParams.gravity = when (pos.lowercase()) {
            "center" -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            "right" -> Gravity.TOP or Gravity.END
            else -> Gravity.TOP or Gravity.START
        }
        
        val colorStr = prefs.getString("speed_indicator_color", "White") ?: "White"
        speedText.setTextColor(
            when (colorStr.lowercase()) {
                "red" -> Color.RED
                "green" -> Color.GREEN
                "blue" -> Color.BLUE
                "yellow" -> Color.YELLOW
                "black" -> Color.BLACK
                else -> Color.WHITE
            }
        )
        
        if (windowTokenObj != null) {
            try {
                windowManager.updateViewLayout(this, layoutParams)
            } catch (e: Exception) {
                AppLogger.d("SpeedOverlayView", "updateAppearance updateViewLayout failed: ${e.message}")
            }
        }
    }

    fun updateSpeed(downBytesPerSec: Long, upBytesPerSec: Long) {
        val downText = formatSpeed(downBytesPerSec)
        val upText = formatSpeed(upBytesPerSec)
        speedText.text = "↓ $downText ↑ $upText"
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        val kbps = bytesPerSec / 1024.0
        return if (kbps > 1024) {
            String.format("%.1f MB/s", kbps / 1024)
        } else {
            String.format("%.1f KB/s", max(0.0, kbps))
        }
    }

    fun attach() {
        if (windowTokenObj == null) {
            try {
                updateAppearance()
                windowManager.addView(this, layoutParams)
                windowTokenObj = "attached"
            } catch (e: Exception) {
                AppLogger.d("SpeedOverlayView", "Failed to attach overlay: ${e.message}")
            }
        }
    }

    fun detach() {
        if (windowTokenObj != null) {
            try {
                windowManager.removeView(this)
                windowTokenObj = null
            } catch (e: Exception) {
                AppLogger.d("SpeedOverlayView", "Failed to detach overlay: ${e.message}")
            }
        }
    }
}
