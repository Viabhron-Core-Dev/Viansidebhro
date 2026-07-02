import re

def update_file(filename):
    with open(filename, 'r') as f:
        content = f.read()

    replacement = """
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
"""

    content = re.sub(r'try \{\n\s*val arr = JSONArray\(pagesJson\)\n\s*for \(i in 0 until arr\.length\(\)\) \{\n\s*list\.add\(SidebarPage\.fromJson\(arr\.getJSONObject\(i\)\)\)\n\s*\}', replacement.strip(), content)

    with open(filename, 'w') as f:
        f.write(content)

update_file('app/src/main/java/com/example/utils/PageManager.kt')
print("Patched PageManager.")
