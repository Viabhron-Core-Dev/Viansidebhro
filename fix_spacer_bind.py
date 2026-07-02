import re

with open('app/src/main/java/com/example/service/AppsPageView.kt', 'r') as f:
    content = f.read()

content = content.replace('fun bind(item: SidebarItem.Spacer) {', 'fun bind(item: SidebarItem.Spacer, position: Int) {')

with open('app/src/main/java/com/example/service/AppsPageView.kt', 'w') as f:
    f.write(content)

print("Fixed SpacerViewHolder.bind.")
