package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.R

@SuppressLint("ViewConstructor")
class SidebarEditOverlayView(
    context: Context,
    private val manager: SidebarAppsManager,
    private val windowManager: WindowManager,
    private val onAddClicked: () -> Unit,
    private val onClose: () -> Unit
) : FrameLayout(context) {

    private val layoutParams: WindowManager.LayoutParams
    private val gridView: GridView
    private val adapter: EditAdapter

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

        setBackgroundColor(Color.parseColor("#E6000000")) // Semi-transparent black

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(40, 100, 40, 100)
        }

        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = "Edit Sidebar Elements"
            setTextColor(Color.WHITE)
            textSize = 24f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = Button(context).apply {
            text = "Done"
            setOnClickListener { close() }
        }

        headerLayout.addView(title)
        headerLayout.addView(btnClose)
        rootLayout.addView(headerLayout)

        val btnAdd = Button(context).apply {
            text = "+ Add Element"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 40, 0, 40)
            }
            setOnClickListener { onAddClicked() }
        }
        rootLayout.addView(btnAdd)

        gridView = GridView(context).apply {
            numColumns = 4
            horizontalSpacing = 20
            verticalSpacing = 40
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        adapter = EditAdapter()
        gridView.adapter = adapter
        rootLayout.addView(gridView)

        addView(rootLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun refresh() {
        adapter.notifyDataSetChanged()
    }

    fun attach() {
        if (windowToken == null) {
            refresh()
            windowManager.addView(this, layoutParams)
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

    private inner class EditAdapter : BaseAdapter() {
        override fun getCount(): Int = manager.activeItems.size

        override fun getItem(position: Int): Any = manager.activeItems[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val item = manager.activeItems[position]
            
            val view = convertView as? LinearLayout ?: LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            view.removeAllViews()

            val density = context.resources.displayMetrics.density
            val size = (56 * density).toInt()

            val iconWrapper = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size)
            }

            val icon = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            if (item is SidebarItem.App) {
                val cached = manager.iconCache.get(item.packageName)
                if (cached != null) {
                    icon.setImageBitmap(cached)
                } else {
                    icon.setImageResource(android.R.mipmap.sym_def_app_icon)
                }
            } else if (item is SidebarItem.SystemAction) {
                icon.setImageResource(item.iconResId)
                icon.setColorFilter(Color.WHITE)
            } else if (item is SidebarItem.VolumeAction) {
                icon.setImageResource(item.iconResId)
                icon.setColorFilter(Color.WHITE)
            } else if (item is SidebarItem.MediaAction) {
                icon.setImageResource(item.iconResId)
                icon.setColorFilter(Color.WHITE)
            } else if (item is SidebarItem.DisplayAction) {
                icon.setImageResource(item.iconResId)
                icon.setColorFilter(Color.WHITE)
            } else if (item is SidebarItem.Folder) {
                val cHex = try { Color.parseColor(item.colorHex) } catch(e:Exception){ Color.parseColor("#00BFA5") }
                val iconC = Color.WHITE
                val miniIcons = item.items.mapNotNull { 
                    if (it.startsWith("app:")) manager.iconCache.get(it.substringAfter("app:")) else null 
                }.take(4)
                icon.setImageDrawable(FolderStyleDrawable(item.folderStyle, cHex, iconC, miniIcons))
            } else if (item is SidebarItem.Link) {
                icon.setImageResource(android.R.drawable.ic_menu_set_as)
                icon.setColorFilter(Color.WHITE)
            } else if (item is SidebarItem.Spacer) {
                icon.setImageResource(android.R.drawable.ic_menu_crop)
                icon.setColorFilter(Color.GRAY)
            }
            iconWrapper.addView(icon)

            val deleteBtn = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_delete)
                setColorFilter(Color.RED)
                layoutParams = FrameLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt(), Gravity.TOP or Gravity.END)
                setOnClickListener {
                    manager.removeItem(item.id)
                    refresh()
                }
            }
            iconWrapper.addView(deleteBtn)

            view.addView(iconWrapper)

            val label = TextView(context).apply {
                text = item.label
                setTextColor(Color.WHITE)
                textSize = 10f
                gravity = Gravity.CENTER
                maxLines = 2
                setPadding(0, 8, 0, 0)
            }
            view.addView(label)

            // Reorder buttons
            val reorderLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val btnLeft = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_media_previous)
                setColorFilter(Color.LTGRAY)
                setPadding(10, 10, 10, 10)
                setOnClickListener {
                    manager.moveItem(item.id, true)
                    refresh()
                }
            }
            val btnRight = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_media_next)
                setColorFilter(Color.LTGRAY)
                setPadding(10, 10, 10, 10)
                setOnClickListener {
                    manager.moveItem(item.id, false)
                    refresh()
                }
            }
            reorderLayout.addView(btnLeft)
            reorderLayout.addView(btnRight)
            view.addView(reorderLayout)

            return view
        }
    }
}
