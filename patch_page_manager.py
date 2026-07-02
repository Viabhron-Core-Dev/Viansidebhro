import re

def update_sidebar_settings(filename):
    with open(filename, 'r') as f:
        content = f.read()

    # When adding a new page:
    # "calculator" -> width: 340, height: 480, useCustomSettings: true
    # "scheduler" -> width: 360, height: 500, useCustomSettings: true
    # "compass" -> width: 300, height: 350, useCustomSettings: true
    replacement = """
                            TextButton(onClick = {
                                val newPages = pages.toMutableList()
                                val newPage = SidebarPage(id = UUID.randomUUID().toString(), type = type, title = title)
                                if (type == "calculator") {
                                    newPage.useCustomSettings = true
                                    newPage.width = 340
                                    newPage.height = 480
                                } else if (type == "scheduler") {
                                    newPage.useCustomSettings = true
                                    newPage.width = 360
                                    newPage.height = 500
                                } else if (type == "compass") {
                                    newPage.useCustomSettings = true
                                    newPage.width = 300
                                    newPage.height = 350
                                } else if (type == "reader") {
                                    newPage.useCustomSettings = true
                                    newPage.width = 380
                                    newPage.height = 600
                                }
                                newPages.add(newPage)
                                pages = newPages
                                savePages()
                                showAddDialog = false
"""
    content = re.sub(r'TextButton\(onClick = \{\n\s*val newPages = pages.toMutableList\(\)\n\s*newPages.add\(SidebarPage\(id = UUID.randomUUID\(\).toString\(\), type = type, title = title\)\)\n\s*pages = newPages\n\s*savePages\(\)\n\s*showAddDialog = false', replacement.strip(), content)

    with open(filename, 'w') as f:
        f.write(content)

update_sidebar_settings('app/src/main/java/com/example/SidebarSettingsScreen.kt')
print("Patched.")
