import re

def update_file(filename):
    with open(filename, 'r') as f:
        content = f.read()

    # In SidebarView.kt, change setColor(Color.argb(alphaInt, 0, 0, 0)) to parse the color
    replacement = """
        val colorHex = prefs.getString("sidebar_color", "#000000") ?: "#000000"
        val baseColor = try { Color.parseColor(colorHex) } catch(e:Exception){ Color.BLACK }
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)
        setColor(Color.argb(alphaInt, r, g, b))
"""

    content = re.sub(r'setColor\(Color\.argb\(alphaInt,\s*0,\s*0,\s*0\)\)', replacement.strip(), content)
    
    # Also fix edit icon color
    content = content.replace('setColorFilter(Color.parseColor("#4CAF50"))', 'setColorFilter(Color.WHITE)')

    with open(filename, 'w') as f:
        f.write(content)

update_file('app/src/main/java/com/example/service/SidebarView.kt')
print("Patched.")
