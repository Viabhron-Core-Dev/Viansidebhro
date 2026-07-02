package com.example.utils

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SidebarPage(
    val id: String,
    val type: String,
    var title: String,
    var gridColumns: Int = 3,
    var gridWrapContent: Boolean = true,
    var stickAlignment: String = "bottom",
    var useCustomSettings: Boolean = false,
    var width: Int = 320,
    var height: Int = 450,
    var wrapContentHeight: Boolean = true,
    var transparency: Float = 0.9f
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("type", type)
        obj.put("title", title)
        obj.put("gridColumns", gridColumns)
        obj.put("gridWrapContent", gridWrapContent)
        obj.put("stickAlignment", stickAlignment)
        obj.put("useCustomSettings", useCustomSettings)
        obj.put("width", width)
        obj.put("height", height)
        obj.put("wrapContentHeight", wrapContentHeight)
        obj.put("transparency", transparency.toDouble())
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): SidebarPage {
            return SidebarPage(
                id = obj.getString("id"),
                type = obj.getString("type"),
                title = obj.getString("title"),
                gridColumns = obj.optInt("gridColumns", 3),
                gridWrapContent = obj.optBoolean("gridWrapContent", true),
                stickAlignment = obj.optString("stickAlignment", "bottom"),
                useCustomSettings = obj.optBoolean("useCustomSettings", false),
                width = obj.optInt("width", 320),
                height = obj.optInt("height", 450),
                wrapContentHeight = obj.optBoolean("wrapContentHeight", true),
                transparency = obj.optDouble("transparency", 0.9).toFloat()
            )
        }
    }
}

object PageManager {
    fun getPages(prefs: SharedPreferences): List<SidebarPage> {
        val pagesJson = prefs.getString("sidebar_pages", null)
        val defaultAppsPage = SidebarPage(id = "default_apps", type = "apps", title = "Apps Grid")
        if (pagesJson == null) {
            // Default setup
            return listOf(defaultAppsPage)
        }
        val list = mutableListOf<SidebarPage>()
        try {
            val arr = JSONArray(pagesJson)
            for (i in 0 until arr.length()) {
                val p = SidebarPage.fromJson(arr.getJSONObject(i))
                if (!p.useCustomSettings) {
                    if (p.type == "calculator") {
                        p.useCustomSettings = true
                        p.width = 340
                        p.height = 480
                    } else if (p.type == "scheduler") {
                        p.useCustomSettings = true
                        p.width = 360
                        p.height = 500
                    } else if (p.type == "compass") {
                        p.useCustomSettings = true
                        p.width = 300
                        p.height = 350
                    } else if (p.type == "reader") {
                        p.useCustomSettings = true
                        p.width = 380
                        p.height = 600
                    }
                }
                list.add(p)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return listOf(defaultAppsPage)
        }
        
        // Ensure first page is always Apps Grid
        if (list.isEmpty()) {
            list.add(defaultAppsPage)
        } else if (list[0].id != "default_apps") {
            list.removeAll { it.id == "default_apps" }
            list.add(0, defaultAppsPage)
        }
        
        return list
    }

    fun savePages(prefs: SharedPreferences, pages: List<SidebarPage>) {
        val arr = JSONArray()
        pages.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("sidebar_pages", arr.toString()).apply()
    }

    fun getDefaultPageIndex(prefs: SharedPreferences): Int {
        return prefs.getInt("sidebar_default_page_index", 0)
    }

    fun saveDefaultPageIndex(prefs: SharedPreferences, index: Int) {
        prefs.edit().putInt("sidebar_default_page_index", index).apply()
    }
}
