def add_imports(filename):
    with open(filename, 'r') as f:
        content = f.read()

    if 'import androidx.compose.foundation.border' not in content:
        content = content.replace('import androidx.compose.foundation.background', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.border')

    with open(filename, 'w') as f:
        f.write(content)

add_imports('app/src/main/java/com/example/SidebarSettingsScreen.kt')
print("Patched.")
