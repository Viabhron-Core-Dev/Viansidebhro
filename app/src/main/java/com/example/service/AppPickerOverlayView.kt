package com.example.service

import android.annotation.SuppressLint
import android.content.Context
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
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("ViewConstructor")
class AppPickerOverlayView(
    context: Context,
    private val manager: SidebarAppsManager,
    private val serviceScope: CoroutineScope,
    private val windowManager: WindowManager,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val layoutParams: WindowManager.LayoutParams
    private val searchInput: EditText
    private val recyclerView: RecyclerView
    private val adapter: PickerAdapter
    private var allApps = listOf<AppInfo>()

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
        recyclerView.setItemViewCacheSize(20)
        
        adapter = PickerAdapter()
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

    private inner class PickerAdapter : RecyclerView.Adapter<PickerViewHolder>() {
        var items: List<AppInfo> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickerViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_picker_app, parent, false)
            return PickerViewHolder(view)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: PickerViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
        }
    }

    private inner class PickerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.picker_app_icon)
        val label: TextView = view.findViewById(R.id.picker_app_label)
        
        fun bind(appInfo: AppInfo) {
            label.text = appInfo.label
            icon.setImageDrawable(null)
            
            itemView.setOnClickListener {
                manager.addApp(appInfo.packageName)
                close()
            }

            serviceScope.launch {
                val bitmap = manager.loadIcon(appInfo.packageName)
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        icon.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }
}
