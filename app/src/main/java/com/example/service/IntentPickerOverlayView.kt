package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("ViewConstructor")
class IntentPickerOverlayView(
    context: Context,
    private val manager: SidebarAppsManager,
    private val serviceScope: CoroutineScope,
    private val windowManager: WindowManager,
    private val targetFolderUuid: String? = null,
    private val onIntentSelected: ((String) -> Unit)? = null,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val layoutParams: WindowManager.LayoutParams
    private val searchInput: EditText
    private val recyclerView: RecyclerView
    private val adapter: IntentPickerAdapter
    private var allApps = listOf<AppInfo>()
    private val pm = context.packageManager

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

        val root = LayoutInflater.from(context).inflate(R.layout.overlay_app_picker, this, true)

        searchInput = root.findViewById(R.id.picker_search)
        recyclerView = root.findViewById(R.id.picker_recycler)
        val closeBtn: TextView = root.findViewById(R.id.picker_close)

        closeBtn.setOnClickListener { close() }

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.setHasFixedSize(true)
        
        adapter = IntentPickerAdapter()
        recyclerView.adapter = adapter

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        isFocusableInTouchMode = true
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                close()
                true
            } else {
                false
            }
        }
    }

    private fun filterList(query: String) {
        val lowerQuery = query.lowercase()
        val filtered = allApps.filter { it.label.lowercase().contains(lowerQuery) }
        adapter.items = filtered
        adapter.notifyDataSetChanged()
    }

    fun attach() {
        if (windowToken == null) {
            allApps = manager.allInstalledApps
            adapter.items = allApps
            adapter.notifyDataSetChanged()
            
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

    private inner class IntentPickerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<AppInfo> = emptyList()
        val expandedApps = mutableSetOf<String>()
        val appActivities = mutableMapOf<String, List<ActivityInfo>>()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_picker_app, parent, false)
            return AppViewHolder(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val app = items[position]
            (holder as AppViewHolder).bind(app)
        }

        inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.picker_app_icon)
            val label: TextView = view.findViewById(R.id.picker_app_label)
            val container = view as LinearLayout
            
            fun bind(appInfo: AppInfo) {
                label.text = appInfo.label
                icon.setImageDrawable(null)
                
                // Remove any previously added activity views
                while (container.childCount > 2) {
                    container.removeViewAt(2)
                }

                val isExpanded = expandedApps.contains(appInfo.packageName)
                if (isExpanded) {
                    val activities = appActivities[appInfo.packageName] ?: emptyList()
                    for (activity in activities) {
                        val actView = TextView(context).apply {
                            text = activity.name.substringAfterLast(".")
                            setPadding(100, 20, 20, 20)
                            setTextColor(android.graphics.Color.WHITE)
                            textSize = 12f
                        }
                        actView.setOnClickListener {
                            val intentId = "intent:${appInfo.packageName}/${activity.name}"
                            if (onIntentSelected != null) {
                                onIntentSelected.invoke(intentId)
                            } else if (targetFolderUuid != null) {
                                manager.addItemToFolder(targetFolderUuid, intentId)
                            } else {
                                manager.addItem(intentId)
                            }
                            close()
                        }
                        container.addView(actView)
                    }
                }

                itemView.setOnClickListener {
                    if (isExpanded) {
                        expandedApps.remove(appInfo.packageName)
                        notifyItemChanged(adapterPosition)
                    } else {
                        expandedApps.add(appInfo.packageName)
                        if (!appActivities.containsKey(appInfo.packageName)) {
                            serviceScope.launch {
                                val acts = loadActivities(appInfo.packageName)
                                withContext(Dispatchers.Main) {
                                    appActivities[appInfo.packageName] = acts
                                    notifyItemChanged(adapterPosition)
                                }
                            }
                        } else {
                            notifyItemChanged(adapterPosition)
                        }
                    }
                }
            }
        }
    }

    private fun loadActivities(packageName: String): List<ActivityInfo> {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }
            val acts = packageInfo.activities ?: return emptyList()
            return acts.filter { it.exported }.sortedBy { it.name }
        } catch (e: Exception) {
            return emptyList()
        }
    }
}
