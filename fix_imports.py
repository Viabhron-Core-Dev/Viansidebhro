def add_imports(filename):
    with open(filename, 'r') as f:
        content = f.read()

    if 'import androidx.compose.foundation.horizontalScroll' not in content:
        content = content.replace('import androidx.compose.foundation.clickable', 'import androidx.compose.foundation.clickable\nimport androidx.compose.foundation.horizontalScroll\nimport androidx.compose.foundation.background')

    with open(filename, 'w') as f:
        f.write(content)

add_imports('app/src/main/java/com/example/SidebarSettingsScreen.kt')
print("Patched.")
