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
    private val pagesList: List<View>,
    private val defaultPageIndex: Int,
    private val onAddClicked: (() -> Unit)? = null,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val layoutParams: WindowManager.LayoutParams
    private val container: FrameLayout
    private val viewPager: ViewPager2
    private val dotsLayout: LinearLayout
    private val pages = mutableListOf<View>()
    private val dots = mutableListOf<View>()

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = context.resources.displayMetrics.density
        val widthDp = prefs.getInt("sidebar_width", 320)
        val widthPx = (widthDp * density).toInt()
        
        val wrapContent = prefs.getBoolean("sidebar_wrap_content", true)
        val heightPx = if (wrapContent) WindowManager.LayoutParams.WRAP_CONTENT else (prefs.getInt("sidebar_height", 450) * density).toInt()

        val isRight = !prefs.getBoolean("sidebar_position_left", false)
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

        val opacity = prefs.getFloat("sidebar_transparency", 0.9f)
        val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)
        
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.argb(alphaInt, 0, 0, 0))
            
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

        val headerHeight = (28 * density).toInt()
        val header = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, headerHeight)
            
            val closeText = TextView(context).apply {
                text = "✕"
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(headerHeight, headerHeight).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
                setOnClickListener { close() }
            }
            addView(closeText)

            val settingsText = TextView(context).apply {
                text = "⚙️"
                textSize = 20f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(headerHeight, headerHeight).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    marginEnd = headerHeight // Position it before the close button
                }
                setOnClickListener {
                    val intent = android.content.Intent(context, com.example.SettingsActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(intent)
                    close()
                }
            }
            addView(settingsText)

            val addText = TextView(context).apply {
                text = "+"
                textSize = 24f
                setTextColor(Color.parseColor("#4CAF50"))
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(headerHeight, headerHeight).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
                setOnClickListener { onAddClicked?.invoke() }
            }
            addView(addText)
        }

        container = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = headerHeight
            }
        }
        
        pages.addAll(pagesList)
        
        viewPager = ViewPager2(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (30 * density).toInt()
            }
        }
        
        val isLooping = pages.size > 2
        val startingIndex = if (isLooping) {
            val half = Int.MAX_VALUE / 2
            half - (half % pages.size) + defaultPageIndex
        } else {
            defaultPageIndex
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
                if (pages.isEmpty()) return
                val actualPos = position % pages.size
                val pageView = pages[actualPos]
                if (pageView.parent != null) {
                    (pageView.parent as ViewGroup).removeView(pageView)
                }
                frame.addView(pageView)
            }
            override fun getItemCount() = if (isLooping) Int.MAX_VALUE else pages.size
        }
        
        dotsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, (30 * density).toInt()).apply {
                gravity = Gravity.BOTTOM
            }
        }
        
        setupDots(pages.size)
        viewPager.setCurrentItem(startingIndex, false)
        updateDots(startingIndex % pages.size)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val actualPos = position % pages.size
                updateDots(actualPos)
                val page = pages.getOrNull(actualPos)
                if (page is AppsPageView) {
                    updateHeight(page.getCurrentHeightPx())
                } else if (page != null) {
                    val density = context.resources.displayMetrics.density
                    updateHeight((450 * density).toInt())
                }
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
        if (count <= 1) {
            dotsLayout.visibility = View.GONE
            return
        }
        dotsLayout.visibility = View.VISIBLE
        val density = context.resources.displayMetrics.density
        val size = (8 * density).toInt()
        val margin = (4 * density).toInt()
        
        for (i in 0 until count) {
            val dot = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, 0, margin, 0)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
            }
            dots.add(dot)
            dotsLayout.addView(dot)
        }
        updateDots(0)
    }
    
    private fun updateDots(position: Int) {
        if (dots.isEmpty()) return
        val density = context.resources.displayMetrics.density
        
        for (i in dots.indices) {
            val bg = dots[i].background as? android.graphics.drawable.GradientDrawable
            if (i == position) {
                bg?.setColor(Color.WHITE)
                dots[i].layoutParams = LinearLayout.LayoutParams((8 * density).toInt(), (8 * density).toInt()).apply {
                    setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
                }
            } else {
                bg?.setColor(Color.parseColor("#44FFFFFF"))
                dots[i].layoutParams = LinearLayout.LayoutParams((6 * density).toInt(), (6 * density).toInt()).apply {
                    setMargins((5 * density).toInt(), 0, (5 * density).toInt(), 0)
                }
            }
        }
    }

    fun updateHeight(pageHeightPx: Int) {
        val wrapContent = prefs.getBoolean("sidebar_wrap_content", true)
        val density = context.resources.displayMetrics.density
        
        if (!wrapContent) {
            layoutParams.height = (prefs.getInt("sidebar_height", 450) * density).toInt()
        } else {
            var targetHeight = pageHeightPx + (28 + 30) * density
            val maxHeight = (prefs.getInt("sidebar_height", 450) * density).toInt()
            
            targetHeight = Math.max((150 * density), targetHeight)
            targetHeight = Math.min(maxHeight.toFloat(), targetHeight)
            
            layoutParams.height = targetHeight.toInt()
        }
        
        if (windowToken != null) {
            windowManager.updateViewLayout(this, layoutParams)
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
    
    fun goToPage(index: Int) {
        if (pages.isEmpty()) return
        val isLooping = pages.size > 2
        if (isLooping) {
            val current = viewPager.currentItem
            val currentActual = current % pages.size
            var diff = index - currentActual
            // take shortest path
            if (diff > pages.size / 2) diff -= pages.size
            if (diff < -pages.size / 2) diff += pages.size
            viewPager.setCurrentItem(current + diff, true)
        } else {
            if (index in 0 until pages.size) {
                viewPager.setCurrentItem(index, true)
            }
        }
    }

    fun getCurrentPageIndex(): Int {
        if (pages.isEmpty()) return 0
        return viewPager.currentItem % pages.size
    }
}
