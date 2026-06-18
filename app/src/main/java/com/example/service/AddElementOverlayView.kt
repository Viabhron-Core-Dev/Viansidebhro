package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R

sealed class AddElementItem {
    data class Header(val title: String) : AddElementItem()
    data class Action(
        val iconResId: Int,
        val title: String,
        val count: String = "",
        val type: ActionType,
        val id: String = "" // Added to support system action IDs
    ) : AddElementItem()
}

enum class ActionType {
    APP, SHORTCUT, FOLDER, LINK, EMPTY_ITEM,
    SYSTEM, VOLUME, MEDIA, BRIGHTNESS, SCREEN_TIMEOUT, SCREEN_ORIENTATION, WIDGET,
    SPECIFIC_SYSTEM_ACTION
}

@SuppressLint("ViewConstructor")
class AddElementOverlayView(
    context: Context,
    private val manager: SidebarAppsManager,
    private val windowManager: WindowManager,
    private val onClose: () -> Unit,
    private val onAppSelected: () -> Unit
) : FrameLayout(context) {

    private val layoutParams: WindowManager.LayoutParams
    private val recyclerView: RecyclerView
    private val adapter: AddElementAdapter

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        val root = LayoutInflater.from(context).inflate(R.layout.overlay_add_element, this, true)

        recyclerView = root.findViewById(R.id.add_element_recycler)
        val closeBtn: View = root.findViewById(R.id.add_element_close)

        closeBtn.setOnClickListener {
            if (currentMode != Mode.MAIN) {
                currentMode = Mode.MAIN
                loadData()
                updateHeaderTitle("Add element")
            } else {
                close()
            }
        }

        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = AddElementAdapter { actionItem ->
            handleActionClick(actionItem)
        }
        recyclerView.adapter = adapter

        isFocusableInTouchMode = true
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (currentMode != Mode.MAIN) {
                    currentMode = Mode.MAIN
                    loadData()
                    updateHeaderTitle("Add element")
                } else {
                    close()
                }
                true
            } else {
                false
            }
        }
        
        loadData()
    }

    private var currentMode = Mode.MAIN

    enum class Mode {
        MAIN, SYSTEM_ACTIONS, VOLUME_ACTIONS, MEDIA_ACTIONS
    }

    private fun loadData() {
        val items = mutableListOf<AddElementItem>()
        
        if (currentMode == Mode.MAIN) {
            // Default actions
            items.add(AddElementItem.Header("Default actions"))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_manage, "App", "(${manager.allInstalledApps.size})", ActionType.APP))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_share, "Shortcut", "(9)", ActionType.SHORTCUT))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_gallery, "Widget", "(50)", ActionType.WIDGET))
            
            // Special items
            items.add(AddElementItem.Header("Special items"))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_gallery, "Folder", "", ActionType.FOLDER))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_set_as, "Link", "", ActionType.LINK))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_close_clear_cancel, "Empty item", "", ActionType.EMPTY_ITEM))
            
            // Android actions
            items.add(AddElementItem.Header("Android actions"))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_info_details, "System", "(${ALL_SYSTEM_ACTIONS.size})", ActionType.SYSTEM))
            items.add(AddElementItem.Action(android.R.drawable.ic_lock_silent_mode_off, "Volume", "(${ALL_VOLUME_ACTIONS.size})", ActionType.VOLUME))
            items.add(AddElementItem.Action(android.R.drawable.ic_media_play, "Media", "(${ALL_MEDIA_ACTIONS.size})", ActionType.MEDIA))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_day, "Brightness", "(11)", ActionType.BRIGHTNESS))
            items.add(AddElementItem.Action(android.R.drawable.ic_lock_idle_low_battery, "Screen timeout", "(7)", ActionType.SCREEN_TIMEOUT))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_always_landscape_portrait, "Screen orientation", "(15)", ActionType.SCREEN_ORIENTATION))
        } else if (currentMode == Mode.SYSTEM_ACTIONS) {
            items.add(AddElementItem.Header("System actions"))
            for (action in ALL_SYSTEM_ACTIONS) {
                items.add(AddElementItem.Action(action.iconResId, action.label, "", ActionType.SPECIFIC_SYSTEM_ACTION, action.id))
            }
        } else if (currentMode == Mode.VOLUME_ACTIONS) {
            items.add(AddElementItem.Header("Volume actions"))
            for (action in ALL_VOLUME_ACTIONS) {
                items.add(AddElementItem.Action(action.iconResId, action.label, "", ActionType.SPECIFIC_SYSTEM_ACTION, action.id))
            }
        } else if (currentMode == Mode.MEDIA_ACTIONS) {
            items.add(AddElementItem.Header("Media actions"))
            for (action in ALL_MEDIA_ACTIONS) {
                items.add(AddElementItem.Action(action.iconResId, action.label, "", ActionType.SPECIFIC_SYSTEM_ACTION, action.id))
            }
        }

        adapter.items = items
        adapter.notifyDataSetChanged()
    }

    private fun handleActionClick(item: AddElementItem.Action) {
        when (item.type) {
            ActionType.APP -> {
                onAppSelected()
            }
            ActionType.SYSTEM -> {
                currentMode = Mode.SYSTEM_ACTIONS
                loadData()
                updateHeaderTitle("System")
            }
            ActionType.VOLUME -> {
                currentMode = Mode.VOLUME_ACTIONS
                loadData()
                updateHeaderTitle("Volume")
            }
            ActionType.MEDIA -> {
                currentMode = Mode.MEDIA_ACTIONS
                loadData()
                updateHeaderTitle("Media")
            }
            ActionType.SPECIFIC_SYSTEM_ACTION -> {
                manager.addItem(item.id)
                close()
            }
            else -> {
                Toast.makeText(context, "${item.type.name} selected (Coming soon)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateHeaderTitle(title: String) {
        val tv = findViewById<TextView>(R.id.add_element_title)
        tv?.text = title
    }

    fun attach() {
        if (windowToken == null) {
            loadData() // Refresh count
            windowManager.addView(this, layoutParams)
            requestFocus()
        }
    }

    fun detach() {
        if (windowToken != null) {
            windowManager.removeView(this)
        }
    }

    private fun close() {
        onClose()
    }

    private inner class AddElementAdapter(
        private val onItemClick: (AddElementItem.Action) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<AddElementItem> = emptyList()

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is AddElementItem.Header -> 0
                is AddElementItem.Action -> 1
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_add_element_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_add_element_row, parent, false)
                ActionViewHolder(view)
            }
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            if (holder is HeaderViewHolder && item is AddElementItem.Header) {
                holder.title.text = item.title
            } else if (holder is ActionViewHolder && item is AddElementItem.Action) {
                holder.title.text = item.title
                holder.icon.setImageResource(item.iconResId)
                holder.count.text = item.count
                holder.count.visibility = if (item.count.isNotEmpty()) View.VISIBLE else View.GONE
                
                holder.itemView.setOnClickListener {
                    onItemClick(item)
                }
            }
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.header_title)
    }

    private class ActionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.item_icon)
        val title: TextView = view.findViewById(R.id.item_title)
        val count: TextView = view.findViewById(R.id.item_count)
    }
}
