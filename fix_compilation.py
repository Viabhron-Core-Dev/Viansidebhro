import re

# Fix AddElementOverlayView
with open('app/src/main/java/com/example/service/AddElementOverlayView.kt', 'r') as f:
    content = f.read()

content = content.replace('MAIN, SYSTEM_ACTIONS, VOLUME_ACTIONS, MEDIA_ACTIONS, DISPLAY_ACTIONS\n    }', 'MAIN, SYSTEM_ACTIONS, VOLUME_ACTIONS, MEDIA_ACTIONS, DISPLAY_ACTIONS, SETTINGS_SHORTCUTS\n    }')

with open('app/src/main/java/com/example/service/AddElementOverlayView.kt', 'w') as f:
    f.write(content)

# Fix AppsPageView
with open('app/src/main/java/com/example/service/AppsPageView.kt', 'r') as f:
    content = f.read()

# remove closeSidebar()
content = content.replace('(context as? FloatingReaderService)?.closeSidebar()', 'context.sendBroadcast(android.content.Intent("com.example.CLOSE_SIDEBAR"))')

# remove holder.icon unresolved reference
content = re.sub(r'\} else if \(item is SidebarItem\.SettingsShortcut\) \{\s*holder\.icon\.setImageResource\(item\.iconResId\)', '} else if (item is SidebarItem.SettingsShortcut) {', content)

with open('app/src/main/java/com/example/service/AppsPageView.kt', 'w') as f:
    f.write(content)

# Fix SidebarAppsManager unresolved reference 'result'
with open('app/src/main/java/com/example/service/SidebarAppsManager.kt', 'r') as f:
    content = f.read()

content = content.replace('result.add(SidebarItem.SettingsShortcut', 'list.add(SidebarItem.SettingsShortcut')

with open('app/src/main/java/com/example/service/SidebarAppsManager.kt', 'w') as f:
    f.write(content)

print("Fixed compilation issues.")
