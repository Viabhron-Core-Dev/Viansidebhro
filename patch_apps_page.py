import re

def update_apps_page(filename):
    with open(filename, 'r') as f:
        content = f.read()

    execution_logic = """
                } else if (item is SidebarItem.SettingsShortcut) {
                    val intent = when (item.action) {
                        "wifi" -> android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                        "bluetooth" -> android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                        "display" -> android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                        "sound" -> android.content.Intent(android.provider.Settings.ACTION_SOUND_SETTINGS)
                        "location" -> android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        "apps" -> android.content.Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS)
                        "security" -> android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                        "battery" -> android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS) // Generic fallback
                        "date" -> android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS)
                        else -> android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                    }
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                        (context as? FloatingReaderService)?.closeSidebar()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Cannot open settings", android.widget.Toast.LENGTH_SHORT).show()
                    }
"""
    content = content.replace('} else if (item is SidebarItem.DisplayAction) {', execution_logic.strip() + '\n                } else if (item is SidebarItem.DisplayAction) {', 1)

    icon_logic = """
            } else if (item is SidebarItem.SettingsShortcut) {
                holder.icon.setImageResource(item.iconResId)
"""
    content = content.replace('} else if (item is SidebarItem.DisplayAction) {', icon_logic.strip() + '\n            } else if (item is SidebarItem.DisplayAction) {')

    with open(filename, 'w') as f:
        f.write(content)

update_apps_page('app/src/main/java/com/example/service/AppsPageView.kt')

def update_sidebar_edit(filename):
    with open(filename, 'r') as f:
        content = f.read()

    content = content.replace('item is SidebarItem.DisplayAction)', 'item is SidebarItem.DisplayAction || item is SidebarItem.SettingsShortcut)')
    
    icon_res = """
                    is SidebarItem.SettingsShortcut -> item.iconResId
                    is SidebarItem.DisplayAction -> item.iconResId
"""
    content = content.replace('is SidebarItem.DisplayAction -> item.iconResId', icon_res.strip())

    with open(filename, 'w') as f:
        f.write(content)

update_sidebar_edit('app/src/main/java/com/example/service/SidebarEditOverlayView.kt')

print("Patched.")
