package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class TriggerHandleView(
    context: Context,
    private val prefs: SharedPreferences,
    private val windowManager: WindowManager,
    private val onTriggerTapped: () -> Unit
) : View(context) {

    private var layoutParams: WindowManager.LayoutParams
    private var initialY = 0
    private var initialTouchY = 0f
    private var isDragging = false
    private val clickSlop = 10f

    init {
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(
                20f, 20f, // top left
                0f, 0f,   // top right
                0f, 0f,   // bottom right
                20f, 20f  // bottom left
            )
            setColor(Color.parseColor("#4DEEEEEE")) 
        }
        background = drawable

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = context.resources.displayMetrics.density
        val widthPx = (5 * density).toInt()
        val heightPx = (100 * density).toInt()

        layoutParams = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            x = 0 
            y = prefs.getInt("trigger_y", 500)
        }

        setupDrag()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = layoutParams.y
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - initialTouchY
                    if (abs(dy) > clickSlop) {
                        isDragging = true
                        layoutParams.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(this, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        Log.d("TriggerHandleView", "trigger tapped")
                        onTriggerTapped()
                    } else {
                        prefs.edit().putInt("trigger_y", layoutParams.y).apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun attach() {
        try {
            if (prefs.getBoolean("trigger_visible", true)) {
                if (windowToken == null) {
                    windowManager.addView(this, layoutParams)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detach() {
        try {
            if (windowToken != null) {
                windowManager.removeView(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
