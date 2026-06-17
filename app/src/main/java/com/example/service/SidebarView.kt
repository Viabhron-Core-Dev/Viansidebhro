package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

@SuppressLint("ViewConstructor")
class SidebarView(
    context: Context,
    private val prefs: SharedPreferences,
    private val windowManager: WindowManager,
    private val defaultPageView: View,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val layoutParams: WindowManager.LayoutParams
    private val container: FrameLayout

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = context.resources.displayMetrics.density
        val widthPx = (260 * density).toInt()
        val heightPx = (context.resources.displayMetrics.heightPixels * 0.8).toInt()

        val gravityEdge = Gravity.END

        layoutParams = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = gravityEdge or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        isFocusableInTouchMode = true
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                close()
                true
            } else {
                false
            }
        }

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor("#E6000000"))
            
            // Adjust corners based on left or right edge
            cornerRadii = floatArrayOf(
                32f, 32f, // top left
                0f, 0f,   // top right
                0f, 0f,   // bottom right
                32f, 32f  // bottom left
            )
        }
        background = drawable

        val header = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (48 * density).toInt())
            
            val closeText = TextView(context).apply {
                text = "✕"
                textSize = 24f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = LayoutParams((48 * density).toInt(), (48 * density).toInt()).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
                setOnClickListener { close() }
            }
            addView(closeText)
        }

        container = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = (48 * density).toInt()
            }
        }
        
        addView(header)
        addView(container)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_OUTSIDE) {
            close()
            return true
        }
        return super.onTouchEvent(event)
    }

    fun close() {
        onClose()
    }

    fun attach() {
        if (windowToken == null) {
            if (defaultPageView.parent != null) {
                (defaultPageView.parent as ViewGroup).removeView(defaultPageView)
            }
            container.addView(defaultPageView)
            windowManager.addView(this, layoutParams)
            requestFocus() // So we can catch BACK key
        }
    }

    fun detach() {
        if (windowToken != null) {
            container.removeView(defaultPageView)
            windowManager.removeView(this)
        }
    }
}
