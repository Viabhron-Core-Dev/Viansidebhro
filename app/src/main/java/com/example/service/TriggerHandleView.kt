package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#804A90E2") // Semi-transparent blue
        style = Paint.Style.FILL
    }

    private val path = Path()

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = context.resources.displayMetrics.density
        val widthPx = (12 * density).toInt()
        val heightPx = (120 * density).toInt()

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val isRight = prefs.getString("trigger_position", "middle_right")?.contains("right") ?: true

        // For a 120 degree angle between the vertical edge and the top/bottom edges,
        // the top edge must be at a 30-degree or 60-degree slope.
        // Let's use tan(30) approx 0.577. 
        val angleHeight = w * 0.577f

        path.reset()
        if (isRight) {
            // Anchor to the right edge
            path.moveTo(w, 0f)
            path.lineTo(0f, angleHeight)
            path.lineTo(0f, h - angleHeight)
            path.lineTo(w, h)
            path.close()
        } else {
            // Anchor to the left edge
            path.moveTo(0f, 0f)
            path.lineTo(w, angleHeight)
            path.lineTo(w, h - angleHeight)
            path.lineTo(0f, h)
            path.close()
        }

        canvas.drawPath(path, paint)
    }

    fun attach() {
        try {
            if (prefs.getBoolean("trigger_visible", true)) {
                if (windowToken == null) {
                    val isRight = prefs.getString("trigger_position", "middle_right")?.contains("right") ?: true
                    layoutParams.gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.TOP
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
