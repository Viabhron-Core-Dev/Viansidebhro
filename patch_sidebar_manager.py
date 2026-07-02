import re

def update_file(filename):
    with open(filename, 'r') as f:
        content = f.read()

    # Add SettingsShortcut to SidebarItem
    settings_action_class = """
    data class SettingsShortcut(
        val action: String,
        override val label: String,
        val iconResId: Int
    ) : SidebarItem() {
        override val id = "settings_shortcut:$action"
    }
"""
    content = content.replace('data class Folder(', settings_action_class.strip() + '\n\n    data class Folder(')

    # Add ALL_SETTINGS_SHORTCUTS
    settings_shortcuts_list = """
val ALL_SETTINGS_SHORTCUTS = listOf(
    SidebarItem.SettingsShortcut("settings", "Settings", android.R.drawable.ic_menu_preferences),
    SidebarItem.SettingsShortcut("wifi", "Wi-Fi", android.R.drawable.ic_menu_preferences),
    SidebarItem.SettingsShortcut("bluetooth", "Bluetooth", android.R.drawable.ic_menu_preferences),
    SidebarItem.SettingsShortcut("display", "Display", android.R.drawable.ic_menu_preferences),
    SidebarItem.SettingsShortcut("sound", "Sound", android.R.drawable.ic_menu_preferences),
    SidebarItem.SettingsShortcut("location", "Location", android.R.drawable.ic_menu_preferences),
    SidebarItem.SettingsShortcut("apps", "Apps", android.R.drawable.ic_menu_preferences),
    SidebarItem.SettingsShortcut("security", "Security", android.R.drawable.ic_menu_preferences),
    SidebarItem.SettingsShortcut("battery", "Battery", android.R.drawable.ic_menu_preferences),
    SidebarItem.SettingsShortcut("date", "Date & Time", android.R.drawable.ic_menu_preferences)
)
"""
    content = content.replace('val ALL_DISPLAY_ACTIONS = listOf(', settings_shortcuts_list.strip() + '\n\nval ALL_DISPLAY_ACTIONS = listOf(')

    # Add parsing logic in SidebarAppsManager
    parsing_logic_1 = """
        } else if (id.startsWith("settings_shortcut:")) {
            val actionId = id.substringAfter("settings_shortcut:")
            val settingsAction = ALL_SETTINGS_SHORTCUTS.find { it.action == actionId }
            if (settingsAction != null) {
                return SidebarItem.SettingsShortcut(actionId, settingsAction.label, settingsAction.iconResId)
            }
"""
    content = content.replace('} else if (id.startsWith("folder:")) {', parsing_logic_1.strip() + '\n        } else if (id.startsWith("folder:")) {', 1)

    parsing_logic_2 = """
            } else if (id.startsWith("settings_shortcut:")) {
                val actionId = id.substringAfter("settings_shortcut:")
                val settingsAction = ALL_SETTINGS_SHORTCUTS.find { it.action == actionId }
                if (settingsAction != null) {
                    result.add(SidebarItem.SettingsShortcut(actionId, settingsAction.label, settingsAction.iconResId))
                }
"""
    # Second occurrence
    content = content.replace('} else if (id.startsWith("folder:")) {', parsing_logic_2.strip() + '\n            } else if (id.startsWith("folder:")) {')

    with open(filename, 'w') as f:
        f.write(content)

update_file('app/src/main/java/com/example/service/SidebarAppsManager.kt')
print("Patched SidebarAppsManager.")
