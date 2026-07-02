import re

with open('app/src/main/java/com/example/service/AppsPageView.kt', 'r') as f:
    content = f.read()

content = content.replace('} else if (item is SidebarItem.SettingsShortcut) {\n            } else if (item is SidebarItem.DisplayAction) {', '} else if (item is SidebarItem.SettingsShortcut) {\n                icon.setBackgroundColor(android.graphics.Color.TRANSPARENT)\n                icon.setImageResource(item.iconResId)\n                icon.setColorFilter(android.graphics.Color.WHITE)\n            } else if (item is SidebarItem.DisplayAction) {')

with open('app/src/main/java/com/example/service/AppsPageView.kt', 'w') as f:
    f.write(content)

print("Fixed AppsPageView.")
