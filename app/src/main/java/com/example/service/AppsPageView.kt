package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("ViewConstructor")
class AppsPageView(
    context: Context,
    private val manager: SidebarAppsManager,
    private val serviceScope: CoroutineScope,
    private val onShowAppPicker: () -> Unit,
    private val onCloseSidebar: () -> Unit
) : FrameLayout(context) {

    private val recyclerView: RecyclerView
    private val adapter: AppsAdapter

    private var displayedItems = listOf<SidebarItem>()
    private val expandedFolders = mutableSetOf<String>()

    init {
        val density = context.resources.displayMetrics.density
        val headerHeight = (48 * density).toInt()

        val header = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, headerHeight)
            
            val addButton = TextView(context).apply {
                text = "+ Add"
                setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.CENTER
                }
                setOnClickListener {
                    onShowAppPicker()
                }
            }
            
            addView(addButton)
        }

        adapter = AppsAdapter()

        recyclerView = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = headerHeight
            }
            layoutManager = GridLayoutManager(context, 3).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return if (this@AppsPageView.adapter.getItemViewType(position) == 1) 3 else 1
                    }
                }
            }
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        recyclerView.adapter = adapter

        addView(header)
        addView(recyclerView)
    }

    private var sourceApps = listOf<SidebarItem>()

    fun updateData(apps: List<SidebarItem>) {
        sourceApps = apps
        refreshList()
    }

    private fun refreshList() {
        val flatList = mutableListOf<SidebarItem>()
        for (item in sourceApps) {
            flatList.add(item)
            if (item is SidebarItem.Folder && expandedFolders.contains(item.id)) {
                for (pkg in item.items) {
                    val appInfo = manager.allInstalledApps.find { it.packageName == pkg }
                    if (appInfo != null) {
                        flatList.add(SidebarItem.App(appInfo.packageName, appInfo.label))
                    }
                }
            }
        }
        displayedItems = flatList
        adapter.notifyDataSetChanged()
    }

    private inner class AppsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemViewType(position: Int): Int {
            return if (displayedItems[position] is SidebarItem.Spacer) 1 else 0
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == 1) {
                val view = View(parent.context)
                return SpacerViewHolder(view)
            }
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_app, parent, false)
            return AppViewHolder(view)
        }

        override fun getItemCount() = displayedItems.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = displayedItems[position]
            if (holder is AppViewHolder) {
                holder.bind(item)
            } else if (holder is SpacerViewHolder && item is SidebarItem.Spacer) {
                holder.bind(item)
            }
        }
    }

    private inner class SpacerViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: SidebarItem.Spacer) {
            val density = view.context.resources.displayMetrics.density
            val lp = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, (item.heightDp * density).toInt())
            view.layoutParams = lp
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            view.setOnLongClickListener {
                manager.removeItem(item.id)
                true
            }
        }
    }

    private inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: android.widget.ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
        
        fun bind(item: SidebarItem) {
            label.text = item.label
            icon.setImageDrawable(null)
            icon.clearColorFilter()
            icon.setBackgroundColor(android.graphics.Color.DKGRAY)
            
            itemView.setOnClickListener {
                if (item is SidebarItem.App) {
                    val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        onCloseSidebar()
                    }
                } else if (item is SidebarItem.Folder) {
                    if (expandedFolders.contains(item.id)) {
                        expandedFolders.remove(item.id)
                    } else {
                        expandedFolders.add(item.id)
                    }
                    refreshList()
                } else if (item is SidebarItem.Link) {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.url))
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    onCloseSidebar()
                } else if (item is SidebarItem.SystemAction) {
                    if (item.action == "log_keeper") {
                        val intent = android.content.Intent(context, com.example.LogKeeperActivity::class.java)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } else if (item.action == "ebook_reader") {
                        val intent = android.content.Intent(context, FloatingReaderService::class.java)
                        intent.putExtra("UNFOLD", true)
                        context.startService(intent)
                    } else if (item.action == "settings") {
                        val intent = android.content.Intent(context, com.example.SettingsActivity::class.java)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } else {
                        val service = VianSideAccessibilityService.instance
                        if (service != null && service.performAction(item.action)) {
                            // success
                            com.example.LogKeeper.writeLog("Sidebar", "System action trigger: ${item.action}")
                        } else {
                            android.widget.Toast.makeText(context, "Please enable VianSide Accessibility Service", android.widget.Toast.LENGTH_SHORT).show()
                            com.example.LogKeeper.writeLog("Sidebar", "Failed system action trigger: ${item.action}")
                            val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    onCloseSidebar()
                } else if (item is SidebarItem.VolumeAction) {
                    try {
                        com.example.LogKeeper.writeLog("Sidebar", "Volume action: ${item.stream}_${item.action}")
                        MediaVolumeHandler.handleVolumeAction(context, item.stream, item.action)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        com.example.LogKeeper.writeLog("Sidebar", "Volume action err: ${e.message}")
                    }
                    onCloseSidebar()
                } else if (item is SidebarItem.MediaAction) {
                    try {
                        com.example.LogKeeper.writeLog("Sidebar", "Media action: ${item.action}")
                        MediaVolumeHandler.handleMediaAction(context, item.action)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        com.example.LogKeeper.writeLog("Sidebar", "Media action err: ${e.message}")
                    }
                    onCloseSidebar()
                } else if (item is SidebarItem.DisplayAction) {
                    try {
                        com.example.LogKeeper.writeLog("Sidebar", "Display action: ${item.action}")
                        DisplayHandler.handleDisplayAction(context, item.action)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        com.example.LogKeeper.writeLog("Sidebar", "Display action err: ${e.message}")
                    }
                    onCloseSidebar()
                }
            }

            if (item is SidebarItem.Folder) {
                itemView.setOnLongClickListener {
                    val actions = arrayOf("Rename", "Add App", "Remove")
                    val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                        .setItems(actions) { _, which ->
                            when (which) {
                                0 -> {
                                    val et = android.widget.EditText(context).apply { setText(item.name) }
                                    val renameDialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                                        .setTitle("Rename")
                                        .setView(et)
                                        .setPositiveButton("Save") { _, _ ->
                                            val newName = et.text.toString()
                                            val json = org.json.JSONObject().apply {
                                                put("name", newName)
                                                put("colorHex", item.colorHex)
                                                put("items", org.json.JSONArray(item.items))
                                            }
                                            // Since addItem does not replace old items easily (unless we just remove and re-add)
                                            // Let's remove and add at end for simplicity
                                            manager.removeItem(item.id)
                                            manager.addItem("folder:${item.uuid}:$json")
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .create()
                                    renameDialog.window?.setType(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else android.view.WindowManager.LayoutParams.TYPE_PHONE)
                                    renameDialog.show()
                                }
                                1 -> {
                                    // Set a flag or handle adding apps to a folder.
                                    // For simplicity, add a random dummy app to demonstrate if picker is hard.
                                    // Or broadcast an intent/callback to show app picker specifically for this folder.
                                    val et = android.widget.EditText(context).apply { hint = "Enter app package (e.g. com.android.chrome)" }
                                    val addAppDialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                                        .setTitle("Add App Package")
                                        .setView(et)
                                        .setPositiveButton("Add") { _, _ ->
                                            val pkg = et.text.toString().trim()
                                            if (pkg.isNotEmpty()) {
                                                val newItems = item.items.toMutableList()
                                                newItems.add(pkg)
                                                val json = org.json.JSONObject().apply {
                                                    put("name", item.name)
                                                    put("colorHex", item.colorHex)
                                                    put("items", org.json.JSONArray(newItems))
                                                }
                                                manager.removeItem(item.id)
                                                manager.addItem("folder:${item.uuid}:$json")
                                            }
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .create()
                                    addAppDialog.window?.setType(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else android.view.WindowManager.LayoutParams.TYPE_PHONE)
                                    addAppDialog.show()
                                }
                                2 -> {
                                    manager.removeItem(item.id)
                                }
                            }
                        }
                        .create()
                    dialog.window?.setType(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else android.view.WindowManager.LayoutParams.TYPE_PHONE)
                    dialog.show()
                    true
                }
            } else {
                itemView.setOnLongClickListener {
                    manager.removeItem(item.id)
                    true
                }
            }

            if (item is SidebarItem.App) {
                serviceScope.launch {
                    val bitmap = manager.loadIcon(item.packageName)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            icon.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            icon.setImageBitmap(bitmap)
                        }
                    }
                }
            } else if (item is SidebarItem.SystemAction) {
                icon.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                icon.setImageResource(item.iconResId)
                icon.setColorFilter(android.graphics.Color.WHITE)
            } else if (item is SidebarItem.VolumeAction) {
                icon.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                icon.setImageResource(item.iconResId)
                icon.setColorFilter(android.graphics.Color.WHITE)
            } else if (item is SidebarItem.MediaAction) {
                icon.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                icon.setImageResource(item.iconResId)
                icon.setColorFilter(android.graphics.Color.WHITE)
            } else if (item is SidebarItem.DisplayAction) {
                icon.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                icon.setImageResource(item.iconResId)
                icon.setColorFilter(android.graphics.Color.WHITE)
            } else if (item is SidebarItem.Folder) {
                icon.setImageDrawable(null)
                icon.clearColorFilter()
                try {
                    icon.setBackgroundColor(android.graphics.Color.parseColor(item.colorHex))
                } catch (e: Exception) {
                    icon.setBackgroundColor(android.graphics.Color.parseColor("#FF5722"))
                }
                
                // Set folder preview icon using a generic folder icon if no apps,
                // or just draw a folder icon over the color background.
                icon.setImageResource(android.R.drawable.ic_dialog_email) // close enough to a folder
                icon.setColorFilter(android.graphics.Color.WHITE)
            } else if (item is SidebarItem.Link) {
                icon.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                icon.setImageResource(android.R.drawable.ic_menu_set_as) // Generic link icon
                icon.setColorFilter(android.graphics.Color.WHITE)
            }
        }
    }
}
