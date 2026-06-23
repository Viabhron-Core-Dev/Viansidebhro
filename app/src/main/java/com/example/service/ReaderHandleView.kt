package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class ReaderHandleView(
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
        color = Color.parseColor("#44102d42") // slightly different hue, semi-transparent
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
        // small semi-circle
        val widthPx = (16 * density).toInt() // width of the handle
        val heightPx = (60 * density).toInt() // height

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
            // Put it slightly above the normal trigger by default
            y = prefs.getInt("reader_trigger_y", prefs.getInt("trigger_y", 500) - (80 * density).toInt())
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
                        onTriggerTapped()
                    } else {
                        prefs.edit().putInt("reader_trigger_y", layoutParams.y).apply()
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

        path.reset()
        if (isRight) {
            // Anchor to the right edge (right side is straight, left side is curved)
            path.moveTo(w, 0f)
            path.cubicTo(0f, 0f, 0f, h, w, h)
            path.close()
        } else {
            // Anchor to the left edge
            path.moveTo(0f, 0f)
            path.cubicTo(w, 0f, w, h, 0f, h)
            path.close()
        }

        canvas.drawPath(path, paint)
    }

    private var isAddedToWindow = false

    fun attach() {
        try {
            if (prefs.getBoolean("reader_handle_enabled", false)) {
                if (!isAddedToWindow) {
                    val isRight = prefs.getString("trigger_position", "middle_right")?.contains("right") ?: true
                    layoutParams.gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.TOP
                    windowManager.addView(this, layoutParams)
                    isAddedToWindow = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detach() {
        try {
            if (isAddedToWindow) {
                windowManager.removeView(this)
                isAddedToWindow = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updatePosition() {
        val isRight = prefs.getString("trigger_position", "middle_right")?.contains("right") ?: true
        layoutParams.gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.TOP
        if (isAddedToWindow) {
            windowManager.updateViewLayout(this, layoutParams)
        }
        invalidate()
    }
}
