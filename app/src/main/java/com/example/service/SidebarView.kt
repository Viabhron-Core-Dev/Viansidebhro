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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

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
    private val viewPager: ViewPager2
    private val dotsLayout: LinearLayout
    private val pages = mutableListOf<View>()
    private val dots = mutableListOf<ImageView>()

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = context.resources.displayMetrics.density
        val widthPx = (200 * density).toInt()
        val heightPx = (360 * density).toInt()

        val isRight = prefs.getString("trigger_position", "middle_right")?.contains("right") ?: true
        val gravityEdge = if (isRight) Gravity.END else Gravity.START

        layoutParams = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = gravityEdge or Gravity.BOTTOM
            x = 0
            y = (48 * density).toInt()
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
            setColor(Color.parseColor("#80000000"))
            
            // Adjust corners based on left or right edge
            cornerRadii = if (isRight) {
                floatArrayOf(
                    20f, 20f, // top left
                    0f, 0f,   // top right
                    0f, 0f,   // bottom right
                    20f, 20f  // bottom left
                )
            } else {
                floatArrayOf(
                    0f, 0f,   // top left
                    20f, 20f, // top right
                    20f, 20f, // bottom right
                    0f, 0f    // bottom left
                )
            }
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
        
        pages.add(defaultPageView)
        
        val placeholderPage = TextView(context).apply {
            text = "Feature coming soon..."
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 16f
        }
        pages.add(placeholderPage)
        
        viewPager = ViewPager2(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                bottomMargin = (30 * density).toInt()
            }
        }
        
        viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val frame = FrameLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
                return object : RecyclerView.ViewHolder(frame) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val frame = holder.itemView as FrameLayout
                frame.removeAllViews()
                val pageView = pages[position]
                if (pageView.parent != null) {
                    (pageView.parent as ViewGroup).removeView(pageView)
                }
                frame.addView(pageView)
            }
            override fun getItemCount() = pages.size
        }
        
        dotsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, (30 * density).toInt()).apply {
                gravity = Gravity.BOTTOM
            }
        }
        
        setupDots(pages.size)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
            }
        })
        
        container.addView(viewPager)
        container.addView(dotsLayout)
        
        addView(header)
        addView(container)
    }

    private fun setupDots(count: Int) {
        dots.clear()
        dotsLayout.removeAllViews()
        val density = context.resources.displayMetrics.density
        val size = (8 * density).toInt()
        val margin = (4 * density).toInt()
        
        for (i in 0 until count) {
            val dot = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, 0, margin, 0)
                }
                setImageResource(android.R.drawable.presence_invisible) // standard dot-like shape
            }
            dots.add(dot)
            dotsLayout.addView(dot)
        }
        updateDots(0)
    }
    
    private fun updateDots(position: Int) {
        val density = context.resources.displayMetrics.density
        val paddingActive = (2 * density).toInt()
        
        for (i in dots.indices) {
            if (i == position) {
                dots[i].setColorFilter(Color.WHITE)
                dots[i].setPadding(paddingActive, paddingActive, paddingActive, paddingActive)
            } else {
                dots[i].setColorFilter(Color.parseColor("#88FFFFFF"))
                dots[i].setPadding(0, 0, 0, 0)
            }
        }
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
            windowManager.addView(this, layoutParams)
            requestFocus() // So we can catch BACK key
        }
    }

    fun detach() {
        if (windowToken != null) {
            windowManager.removeView(this)
        }
    }
}
