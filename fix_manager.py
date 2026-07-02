import re

with open('app/src/main/java/com/example/service/SidebarAppsManager.kt', 'r') as f:
    content = f.read()

content = content.replace('list.add(SidebarItem.SettingsShortcut(actionId, settingsAction.label, settingsAction.iconResId))', 'return SidebarItem.SettingsShortcut(actionId, settingsAction.label, settingsAction.iconResId)', 1)
content = content.replace('list.add(SidebarItem.SettingsShortcut(actionId, settingsAction.label, settingsAction.iconResId))', 'result.add(SidebarItem.SettingsShortcut(actionId, settingsAction.label, settingsAction.iconResId))')

with open('app/src/main/java/com/example/service/SidebarAppsManager.kt', 'w') as f:
    f.write(content)

print("Fixed SidebarAppsManager.")
