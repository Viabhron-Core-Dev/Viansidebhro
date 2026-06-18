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
            
            val title = TextView(context).apply {
                text = "Apps"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                    marginStart = padding16
                }
            }
            
            val addButton = TextView(context).apply {
                text = "+ Add"
                setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(padding16, 0, padding16, 0)
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                }
                setOnClickListener {
                    onShowAppPicker()
                }
            }
            
            addView(title)
            addView(addButton)
        }

        recyclerView = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = headerHeight
            }
            layoutManager = GridLayoutManager(context, 3) 
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }

        adapter = AppsAdapter()
        recyclerView.adapter = adapter

        addView(header)
        addView(recyclerView)
    }

    fun updateData(apps: List<AppInfo>) {
        adapter.items = apps
        adapter.notifyDataSetChanged()
    }

    private inner class AppsAdapter : RecyclerView.Adapter<AppViewHolder>() {
        var items: List<AppInfo> = emptyList()

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
        
        fun bind(appInfo: AppInfo) {
            label.text = appInfo.label
            icon.setImageDrawable(null)
            icon.setBackgroundColor(android.graphics.Color.DKGRAY)
            
            itemView.setOnClickListener {
                val intent = context.packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    onCloseSidebar()
                }
            }

            itemView.setOnLongClickListener {
                manager.removeApp(appInfo.packageName)
                true
            }

            serviceScope.launch {
                val bitmap = manager.loadIcon(appInfo.packageName)
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        icon.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        icon.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }
}
