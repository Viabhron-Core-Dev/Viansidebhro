import re

def update_handle_edit(filename):
    with open(filename, 'r') as f:
        content = f.read()

    # Add sky blue to presetColors
    content = content.replace('"#80FFEB3B"\n                )', '"#80FFEB3B", "#8087CEEB"\n                )')
    
    with open(filename, 'w') as f:
        f.write(content)

def update_sidebar_settings(filename):
    with open(filename, 'r') as f:
        content = f.read()

    # Add color state
    content = content.replace('var sidebarTransparency', 'var sidebarColorHex by remember { mutableStateOf(prefs.getString("sidebar_color", "#000000") ?: "#000000") }\n    var sidebarTransparency')
    
    # Add Color Picker row
    color_picker_code = """
                ListItem(
                    headlineContent = { Text("Sidebar Color") },
                    supportingContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val presetColors = listOf(
                                "#000000", "#FFFFFF", "#FF5252", "#4CAF50", "#2196F3", "#FFEB3B", "#87CEEB"
                            )
                            presetColors.forEach { colorString ->
                                val parsedColor = try {
                                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(colorString))
                                } catch (e: Exception) {
                                    androidx.compose.ui.graphics.Color.Gray
                                }
                                val baseColorStr = if (colorString.length >= 7) colorString.substring(colorString.length - 6) else colorString
                                val currentBaseStr = if (sidebarColorHex.length >= 7) sidebarColorHex.substring(sidebarColorHex.length - 6) else sidebarColorHex
                                val isSelected = baseColorStr.equals(currentBaseStr, ignoreCase = true)
                                
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(parsedColor, androidx.compose.foundation.shape.CircleShape)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Gray,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable {
                                            sidebarColorHex = colorString
                                            prefs.edit().putString("sidebar_color", colorString).apply()
                                        }
                                )
                            }
                        }
                    }
                )
                Divider()
    """
    content = content.replace('ListItem(\n                    headlineContent = { Text("Background Opacity") },', color_picker_code.strip() + '\n                ListItem(\n                    headlineContent = { Text("Background Opacity") },')

    # Replace long click logic and Dropdown menu
    item_replace = """
            itemsIndexed(pages) { index, page ->
                Box {
                    ListItem(
                        modifier = Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = {
                                if (index > 0 && pages.size > 1) { // don't allow editing/removing default Apps Grid
                                    selectedActionPage = page
                                    pageActionIndex = index
                                }
                            }
                        ),
                        headlineContent = { Text(page.title) },
                        supportingContent = { Text(page.type.replace("_", " ").capitalize()) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = {
                                    if (index > 1) { // 1 instead of 0 to protect index 0 apps grid
                                        val newPages = pages.toMutableList()
                                        val temp = newPages[index]
                                        newPages[index] = newPages[index - 1]
                                        newPages[index - 1] = temp
                                        if (defaultIndex == index) defaultIndex = index - 1
                                        else if (defaultIndex == index - 1) defaultIndex = index
                                        pages = newPages
                                        savePages()
                                    }
                                }, enabled = index > 1) {
                                    Icon(Icons.Default.ArrowUpward, "Up")
                                }
                                IconButton(onClick = {
                                    if (index > 0 && index < pages.size - 1) {
                                        val newPages = pages.toMutableList()
                                        val temp = newPages[index]
                                        newPages[index] = newPages[index + 1]
                                        newPages[index + 1] = temp
                                        if (defaultIndex == index) defaultIndex = index + 1
                                        else if (defaultIndex == index + 1) defaultIndex = index
                                        pages = newPages
                                        savePages()
                                    }
                                }, enabled = index > 0 && index < pages.size - 1) {
                                    Icon(Icons.Default.ArrowDownward, "Down")
                                }
                            }
                        }
                    )
                    
                    DropdownMenu(
                        expanded = selectedActionPage == page && pageActionIndex == index,
                        onDismissRequest = {
                            selectedActionPage = null
                            pageActionIndex = -1
                        }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit / Customize") },
                            onClick = {
                                customisingPage = selectedActionPage
                                selectedActionPage = null
                                pageActionIndex = -1
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                val newPages = pages.toMutableList()
                                newPages.removeAt(pageActionIndex)
                                if (defaultIndex == pageActionIndex) defaultIndex = 0
                                else if (defaultIndex > pageActionIndex) defaultIndex--
                                pages = newPages
                                savePages()
                                
                                selectedActionPage = null
                                pageActionIndex = -1
                            }
                        )
                    }
                }
                Divider()
            }
"""
    # First we need to extract the exact itemsIndexed(pages) loop to replace it
    content = re.sub(r'itemsIndexed\(pages\) \{ index, page ->.*?Divider\(\)\n            \}', item_replace.strip(), content, flags=re.DOTALL)
    
    # Remove the AlertDialog for selectedActionPage
    content = re.sub(r'if \(selectedActionPage != null && pageActionIndex != -1\) \{.*?AlertDialog\(.*?\n        \}', '', content, flags=re.DOTALL)
    
    with open(filename, 'w') as f:
        f.write(content)

update_handle_edit('app/src/main/java/com/example/HandleEditScreen.kt')
update_sidebar_settings('app/src/main/java/com/example/SidebarSettingsScreen.kt')
print("Patched settings.")
