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

    init {
        val density = context.resources.displayMetrics.density
        val headerHeight = (48 * density).toInt()
        val padding16 = (16 * density).toInt()

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

        recyclerView = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = headerHeight
            }
            layoutManager = GridLayoutManager(context, 2) 
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        adapter = AppsAdapter()
        recyclerView.adapter = adapter

        addView(header)
        addView(recyclerView)
    }

    fun updateData(apps: List<SidebarItem>) {
        adapter.items = apps
        adapter.notifyDataSetChanged()
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>() {
        var items: List<SidebarItem> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sidebar_app, parent, false)
            return AppViewHolder(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
        }
    }

    private inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: android.widget.ImageView = view.findViewById(R.id.app_icon)
        val label: TextView = view.findViewById(R.id.app_label)
        
        fun bind(item: SidebarItem) {
            label.text = item.label
            icon.setImageDrawable(null)
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

            itemView.setOnLongClickListener {
                manager.removeItem(item.id)
                true
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
            }
        }
    }
}
