package com.example.service

import android.app.AlertDialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

fun showFolderStyleDialog(context: Context, item: SidebarItem.Folder, manager: SidebarAppsManager, onStyleSelected: ((Int) -> Unit)? = null) {
    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(40, 40, 40, 40)
        setBackgroundColor(Color.WHITE)
    }

    val title = TextView(context).apply {
        text = "Folder style"
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.BLACK)
        setPadding(0, 0, 0, 40)
    }
    layout.addView(title)

    val gridView = GridView(context).apply {
        numColumns = 2
        verticalSpacing = 40
        horizontalSpacing = 20
    }

    val styles = listOf(
        "Folder", "Stack",
        "Tile", "Action folder",
        "Folder\nCircle + Background", "Stack\nCircle + Background",
        "Tile\nCircle + Background", "Action folder\nCircle + Background",
        "Folder\nCircle (Border)", "Stack\nCircle (Border)",
        "Tile\nCircle (Border)", "Action folder\nCircle (Border)"
    )

    val adapter = object : BaseAdapter() {
        override fun getCount(): Int = styles.size
        override fun getItem(position: Int): Any = styles[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView as? LinearLayout ?: LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            view.removeAllViews()

            val density = context.resources.displayMetrics.density
            val size = (64 * density).toInt()

            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size)
                val miniIcons = item.items.mapNotNull { 
                    if (it.startsWith("app:")) manager.iconCache.get(it.substringAfter("app:")) else null 
                }.take(4)
                setImageDrawable(FolderStyleDrawable(position, Color.parseColor("#00BFA5"), Color.parseColor("#333333"), miniIcons))
            }
            view.addView(iv)

            val tv = TextView(context).apply {
                text = styles[position]
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                setPadding(0, 10, 0, 0)
                if (position == item.folderStyle) {
                    setTypeface(null, Typeface.BOLD)
                }
            }
            view.addView(tv)

            return view
        }
    }

    gridView.adapter = adapter
    layout.addView(gridView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

    val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
        .setView(layout)
        .setPositiveButton("OK", null)
        .create()

    gridView.setOnItemClickListener { _, _, position, _ ->
        if (onStyleSelected != null) {
            onStyleSelected(position)
        } else {
            val json = org.json.JSONObject().apply {
                put("name", item.name)
                put("colorHex", item.colorHex)
                val jArr = org.json.JSONArray()
                item.items.forEach { jArr.put(it) }
                put("items", jArr)
                put("folderStyle", position)
            }
            manager.removeItem(item.id)
            manager.addItem("folder:${item.uuid}:$json")
        }
        dialog.dismiss()
    }

    dialog.window?.setType(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE)
    dialog.show()
}

class FolderStyleDrawable(
    private val styleIndex: Int,
    private val themeColor: Int,
    private val iconColor: Int,
    private val miniIcons: List<Bitmap> = emptyList()
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        val cx = bounds.centerX().toFloat()
        val cy = bounds.centerY().toFloat()
        
        val baseStyle = styleIndex % 4
        val containerStyle = styleIndex / 4

        // Draw Container
        if (containerStyle == 1) { // Circle + Background
            paint.style = Paint.Style.FILL
            paint.color = themeColor
            paint.alpha = 50
            canvas.drawCircle(cx, cy, w / 2f, paint)
        } else if (containerStyle == 2) { // Circle Border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w * 0.05f
            paint.color = themeColor
            paint.alpha = 255
            canvas.drawCircle(cx, cy, w / 2f - paint.strokeWidth / 2f, paint)
        }

        // Draw Base Symbol
        val symbolSize = if (containerStyle == 0) w * 0.7f else w * 0.5f
        val sx = cx - symbolSize / 2f
        val sy = cy - symbolSize / 2f
        
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        
        when (baseStyle) {
            0 -> drawFolder(canvas, sx, sy, symbolSize, iconColor)
            1 -> drawStack(canvas, sx, sy, symbolSize, themeColor)
            2 -> drawTile(canvas, sx, sy, symbolSize, themeColor)
            3 -> drawActionFolder(canvas, sx, sy, symbolSize, themeColor, iconColor)
        }
    }
    
    private fun drawFolder(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        paint.color = color
        val path = Path()
        val tabW = size * 0.4f
        val tabH = size * 0.2f
        val r = size * 0.05f
        path.moveTo(x, y + size)
        path.lineTo(x, y + tabH)
        path.lineTo(x + tabW - r, y + tabH)
        path.lineTo(x + tabW + r, y)
        path.lineTo(x + size, y)
        path.lineTo(x + size, y + size)
        path.close()
        canvas.drawPath(path, paint)
        
        drawMiniIconsInGrid(canvas, x + size * 0.1f, y + size * 0.3f, size * 0.8f, size * 0.6f)
    }
    
    private fun drawStack(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        paint.color = color
        val count = 4
        val gap = size * 0.15f
        val rh = (size - gap * (count - 1)) / count
        for (i in 0 until count) {
            val ty = y + i * (rh + gap)
            val rw = size * (1f - (i * 0.15f))
            canvas.drawRoundRect(RectF(x, ty, x + rw, ty + rh), rh/2, rh/2, paint)
        }
    }
    
    private fun drawTile(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        paint.color = color
        val gap = size * 0.1f
        val ts = (size - gap) / 2f
        
        // Draw tiles or mini icons
        if (miniIcons.isNotEmpty()) {
            for (i in 0..1) {
                for (j in 0..1) {
                    val index = i * 2 + j
                    val tx = x + j * (ts + gap)
                    val ty = y + i * (ts + gap)
                    if (index < miniIcons.size) {
                        canvas.drawBitmap(miniIcons[index], null, RectF(tx, ty, tx + ts, ty + ts), iconPaint)
                    } else {
                        canvas.drawRoundRect(RectF(tx, ty, tx + ts, ty + ts), ts/4, ts/4, paint)
                    }
                }
            }
        } else {
            for (i in 0..1) {
                for (j in 0..1) {
                    val tx = x + j * (ts + gap)
                    val ty = y + i * (ts + gap)
                    val tw = if (i == 0 && j == 1) ts * 0.6f else ts
                    val tw2 = if (i == 1 && j == 1) ts * 0.8f else tw
                    canvas.drawRoundRect(RectF(tx, ty, tx + tw2, ty + ts), ts/4, ts/4, paint)
                }
            }
        }
    }
    
    private fun drawActionFolder(canvas: Canvas, x: Float, y: Float, size: Float, color: Int, iconColor: Int) {
        drawStack(canvas, x, y, size, color)
        val fs = size * 0.5f
        drawFolder(canvas, x + size - fs, y + size - fs, fs, iconColor)
    }

    private fun drawMiniIconsInGrid(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        if (miniIcons.isEmpty()) return
        val cols = 2
        val rows = 2
        val padding = w * 0.05f
        val iconW = (w - padding * (cols + 1)) / cols
        val iconH = (h - padding * (rows + 1)) / rows
        val size = minOf(iconW, iconH)
        
        for (i in 0 until minOf(4, miniIcons.size)) {
            val row = i / cols
            val col = i % cols
            val ix = x + padding + col * (size + padding)
            val iy = y + padding + row * (size + padding)
            canvas.drawBitmap(miniIcons[i], null, RectF(ix, iy, ix + size, iy + size), iconPaint)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
