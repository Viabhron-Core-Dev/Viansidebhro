import re

with open('app/src/main/java/com/example/service/AppsPageView.kt', 'r') as f:
    content = f.read()

# Fix 1: updateItems and proper folder parsing
# Add updateItems to adapter
update_items = """
        fun updateItems(newItems: List<SidebarItem>) {
            notifyDataSetChanged()
        }
"""
content = content.replace('override fun getItemViewType(position: Int): Int {', update_items.strip() + '\n\n        override fun getItemViewType(position: Int): Int {')

# Fix refreshList()
refresh_list = """
    private fun refreshList() {
        val flatList = mutableListOf<SidebarItem>()
        for (item in sourceApps) {
            flatList.add(item)
            if (item is SidebarItem.Folder && expandedFolders.contains(item.id)) {
                for (itemId in item.items) {
                    val parsedItem = manager.parseId(itemId)
                    if (parsedItem != null) {
                        flatList.add(parsedItem)
                    }
                }
            }
        }
        displayedItems = flatList
        adapter.notifyDataSetChanged()
"""
content = re.sub(r'private fun refreshList\(\) \{.*?adapter\.notifyDataSetChanged\(\)', refresh_list.strip(), content, flags=re.DOTALL)


# Fix 2: folder icon loading
# Change bind signature to take position
content = content.replace('fun bind(item: SidebarItem) {', 'fun bind(item: SidebarItem, position: Int) {')
content = content.replace('holder.bind(item)', 'holder.bind(item, position)')

# Modify the folder icon handling inside AppViewHolder
folder_icon = """
            } else if (item is SidebarItem.Folder) {
                icon.setImageDrawable(null)
                icon.clearColorFilter()
                icon.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                val cHex = try { android.graphics.Color.parseColor(item.colorHex) } catch(e:Exception){ android.graphics.Color.parseColor("#00BFA5") }
                val iconC = android.graphics.Color.WHITE
                
                val miniIcons = item.items.mapNotNull { 
                    if (it.startsWith("app:")) manager.iconCache.get(it.substringAfter("app:")) else null 
                }.take(4)
                icon.setImageDrawable(FolderStyleDrawable(item.folderStyle, cHex, iconC, miniIcons))
                
                if (miniIcons.isEmpty() && item.items.any { it.startsWith("app:") }) {
                    serviceScope.launch {
                        var loadedAny = false
                        for (it in item.items.take(4)) {
                            if (it.startsWith("app:")) {
                                val bitmap = manager.loadIcon(it.substringAfter("app:"))
                                if (bitmap != null) {
                                    loadedAny = true
                                }
                            }
                        }
                        if (loadedAny) {
                            withContext(Dispatchers.Main) {
                                adapter.notifyItemChanged(position)
                            }
                        }
                    }
                }
            } else if (item is SidebarItem.Link) {
"""
content = re.sub(r'\} else if \(item is SidebarItem\.Folder\) \{.*?(?=\} else if \(item is SidebarItem\.Link\) \{)', folder_icon.strip() + '\n            ', content, flags=re.DOTALL)

with open('app/src/main/java/com/example/service/AppsPageView.kt', 'w') as f:
    f.write(content)

print("Fixed AppsPageView.")
