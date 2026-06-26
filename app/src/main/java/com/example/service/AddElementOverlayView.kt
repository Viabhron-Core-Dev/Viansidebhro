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
    APP, SHORTCUT, FOLDER, LINK, EMPTY_ITEM, INTENT,
    SYSTEM, VOLUME, MEDIA, BRIGHTNESS, SCREEN_TIMEOUT, SCREEN_ORIENTATION, WIDGET,
    SPECIFIC_SYSTEM_ACTION
}

@SuppressLint("ViewConstructor")
class AddElementOverlayView(
    context: Context,
    private val manager: SidebarAppsManager,
    private val windowManager: WindowManager,
    private val targetFolderUuid: String? = null,
    private val onClose: () -> Unit,
    private val onAppSelected: (String?) -> Unit,
    private val onElementSelected: ((String) -> Unit)? = null
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
        MAIN, SYSTEM_ACTIONS, VOLUME_ACTIONS, MEDIA_ACTIONS, DISPLAY_ACTIONS
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
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_agenda, "Intent", "", ActionType.INTENT))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_set_as, "Link", "", ActionType.LINK))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_close_clear_cancel, "Empty item", "", ActionType.EMPTY_ITEM))
            
            // Android actions
            items.add(AddElementItem.Header("Android actions"))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_info_details, "System", "(${ALL_SYSTEM_ACTIONS.size})", ActionType.SYSTEM))
            items.add(AddElementItem.Action(android.R.drawable.ic_lock_silent_mode_off, "Volume", "(${ALL_VOLUME_ACTIONS.size})", ActionType.VOLUME))
            items.add(AddElementItem.Action(android.R.drawable.ic_media_play, "Media", "(${ALL_MEDIA_ACTIONS.size})", ActionType.MEDIA))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_day, "Display Controls", "(${ALL_DISPLAY_ACTIONS.size})", ActionType.BRIGHTNESS))
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
        } else if (currentMode == Mode.DISPLAY_ACTIONS) {
            items.add(AddElementItem.Header("Display actions"))
            for (action in ALL_DISPLAY_ACTIONS) {
                items.add(AddElementItem.Action(action.iconResId, action.label, "", ActionType.SPECIFIC_SYSTEM_ACTION, action.id))
            }
        }

        adapter.items = items
        adapter.notifyDataSetChanged()
    }

    private fun addSidebarItem(itemId: String) {
        if (onElementSelected != null) {
            onElementSelected.invoke(itemId)
            return
        }
        if (targetFolderUuid != null) {
            manager.addItemToFolder(targetFolderUuid, itemId)
        } else {
            manager.addItem(itemId)
        }
    }

    private fun handleActionClick(item: AddElementItem.Action) {
        when (item.type) {
            ActionType.APP -> {
                onAppSelected(targetFolderUuid)
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
            ActionType.BRIGHTNESS -> {
                currentMode = Mode.DISPLAY_ACTIONS
                loadData()
                updateHeaderTitle("Display")
            }
            ActionType.SPECIFIC_SYSTEM_ACTION -> {
                addSidebarItem(item.id)
                close()
            }
            ActionType.FOLDER -> {
                val et = android.widget.EditText(context).apply { hint = "Folder name" }
                val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle("New Folder")
                    .setView(et)
                    .setPositiveButton("Create") { _, _ ->
                        val name = et.text.toString().ifEmpty { "New Folder" }
                        val uuid = java.util.UUID.randomUUID().toString()
                        val color = "#FF5722" // default color
                        
                        // Show style dialog immediately!
                        val dummyFolder = SidebarItem.Folder(uuid, name, color, emptyList(), 0)
                        showFolderStyleDialog(context, dummyFolder, manager) { styleIndex ->
                            val json = org.json.JSONObject().apply {
                                put("name", name)
                                put("colorHex", color)
                                put("items", org.json.JSONArray())
                                put("folderStyle", styleIndex)
                            }
                            addSidebarItem("folder:$uuid:$json")
                            close()
                            // Note: we should show the add element overlay for this folder right away,
                            // but the caller of AddElementOverlayView should ideally handle it, or we can broadcast it.
                            // For simplicity, we can restart AddElementOverlayView with this folder ID.
                            val svc = (context as? FloatingReaderService)
                            svc?.showAddElementOverlay(uuid)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .create()
                dialog.window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE)
                dialog.show()
            }
            ActionType.SHORTCUT -> {
                val intent = android.content.Intent(context, com.example.ShortcutHandlerActivity::class.java).apply {
                    putExtra("ACTION_TYPE", "PICK")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (targetFolderUuid != null) {
                    intent.putExtra("FOLDER_UUID", targetFolderUuid)
                }
                if (onElementSelected != null) {
                    intent.putExtra("IS_ELEMENT_CALLBACK", true)
                }
                context.startActivity(intent)
                close()
            }
            ActionType.INTENT -> {
                val svc = (context as? FloatingReaderService)
                svc?.showIntentPicker(targetFolderUuid, onElementSelected)
                close()
            }
            ActionType.LINK -> {
                val layout = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(50, 20, 50, 20)
                }
                val labelEt = android.widget.EditText(context).apply { hint = "Label" }
                val urlEt = android.widget.EditText(context).apply { hint = "URL or intent://" }
                layout.addView(labelEt)
                layout.addView(urlEt)

                val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle("New Link")
                    .setView(layout)
                    .setPositiveButton("Add") { _, _ ->
                        val label = labelEt.text.toString().ifEmpty { "Link" }
                        val url = urlEt.text.toString().ifEmpty { "https://google.com" }
                        val uuid = java.util.UUID.randomUUID().toString()
                        val json = org.json.JSONObject().apply {
                            put("label", label)
                            put("url", url)
                        }
                        addSidebarItem("link:$uuid:$json")
                        close()
                    }
                    .setNegativeButton("Cancel", null)
                    .create()
                dialog.window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE)
                dialog.show()
            }
            ActionType.EMPTY_ITEM -> {
                val et = android.widget.EditText(context).apply { 
                    hint = "Height in DP (e.g. 20)" 
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER
                }
                val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                    .setTitle("Add Spacer")
                    .setView(et)
                    .setPositiveButton("Add") { _, _ ->
                        var height = 20
                        try {
                            height = et.text.toString().toInt()
                        } catch (e: Exception) {}
                        val uuid = java.util.UUID.randomUUID().toString()
                        val json = org.json.JSONObject().apply {
                            put("heightDp", height)
                        }
                        addSidebarItem("spacer:$uuid:$json")
                        close()
                    }
                    .setNegativeButton("Cancel", null)
                    .create()
                dialog.window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE)
                dialog.show()
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
