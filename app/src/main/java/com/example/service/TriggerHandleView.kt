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
import android.view.GestureDetector
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
    
    private val prefix = "handle_sidebar_"

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val path = Path()

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isDragging) {
                val tapAction = prefs.getString("${prefix}tap", "none") ?: "none"
                if (tapAction != "none") {
                    handleAction("tap")
                } else {
                    onTriggerTapped()
                }
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            handleAction("double_tap")
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            handleAction("long_press")
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dx) > abs(dy)) {
                if (abs(velocityX) > 100) {
                    if (dx > 0) handleAction("swipe_right") else handleAction("swipe_left")
                    return true
                }
            } else {
                if (abs(velocityY) > 100) {
                    if (dy > 0) handleAction("swipe_down") else handleAction("swipe_up")
                    return true
                }
            }
            return false
        }
    })

    private fun handleAction(gesture: String) {
        val action = prefs.getString("$prefix$gesture", "none") ?: "none"
        if (action == "toggle_sidebar") {
            onTriggerTapped()
        } else if (action == "toggle_reader") {
            FloatingReaderService.instance?.toggleReader()
        } else if (action.startsWith("open_")) {
            val pageType = action.removePrefix("open_")
            FloatingReaderService.instance?.openSidebarPage(pageType)
        } else if (action.startsWith("action_")) {
            val sysAction = action.removePrefix("action_")
            VianSideAccessibilityService.instance?.performAction(sysAction)
        }
    }

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = context.resources.displayMetrics.density
        val w = prefs.getInt("${prefix}width", 6)
        val h = prefs.getInt("${prefix}height", 120)
        
        val widthPx = (w * density).toInt()
        val heightPx = (h * density).toInt()

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
            y = prefs.getInt("${prefix}y", 500)
        }

        setupDrag()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        setOnTouchListener { _, event ->
            val gestureHandled = gestureDetector.onTouchEvent(event)
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
                    if (isDragging) {
                        prefs.edit().putInt("${prefix}y", layoutParams.y).apply()
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
        val isRight = prefs.getString("sidebar_position", "right") == "right"
        
        val colorHex = prefs.getString("${prefix}color", "#3318304A") ?: "#3318304A"
        try {
            paint.color = Color.parseColor(colorHex)
        } catch (e: Exception) {
            paint.color = Color.parseColor("#3318304A")
        }

        path.reset()
        val shape = prefs.getString("${prefix}shape", "triangle") ?: "triangle"
        
        when (shape) {
            "rectangle" -> {
                path.addRect(0f, 0f, w, h, Path.Direction.CW)
            }
            "rounded_rect" -> {
                val radius = w / 2f
                if (isRight) {
                    path.addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius), Path.Direction.CW)
                } else {
                    path.addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f), Path.Direction.CW)
                }
            }
            "half_oval" -> {
                if (isRight) {
                    path.moveTo(w, 0f)
                    path.cubicTo(0f, 0f, 0f, h, w, h)
                    path.close()
                } else {
                    path.moveTo(0f, 0f)
                    path.cubicTo(w, 0f, w, h, 0f, h)
                    path.close()
                }
            }
            else -> { // triangle
                val angleHeight = w * 0.577f
                if (isRight) {
                    path.moveTo(w, 0f)
                    path.lineTo(0f, angleHeight)
                    path.lineTo(0f, h - angleHeight)
                    path.lineTo(w, h)
                    path.close()
                } else {
                    path.moveTo(0f, 0f)
                    path.lineTo(w, angleHeight)
                    path.lineTo(w, h - angleHeight)
                    path.lineTo(0f, h)
                    path.close()
                }
            }
        }

        canvas.drawPath(path, paint)
    }

    private var isAddedToWindow = false

    fun attach() {
        try {
            if (prefs.getBoolean("trigger_visible", true)) {
                if (!isAddedToWindow) {
                    val isRight = prefs.getString("sidebar_position", "right") == "right"
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
        val density = context.resources.displayMetrics.density
        val w = prefs.getInt("${prefix}width", 6)
        val h = prefs.getInt("${prefix}height", 120)
        
        layoutParams.width = (w * density).toInt()
        layoutParams.height = (h * density).toInt()
        layoutParams.y = prefs.getInt("${prefix}y", 500)
        
        val isRight = prefs.getString("sidebar_position", "right") == "right"
        layoutParams.gravity = (if (isRight) Gravity.END else Gravity.START) or Gravity.TOP
        
        if (isAddedToWindow) {
            windowManager.updateViewLayout(this, layoutParams)
        }
        invalidate()
    }
}
