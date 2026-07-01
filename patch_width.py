import re

def update_file(filename):
    with open(filename, 'r') as f:
        content = f.read()

    # Add LocalConfiguration import if not there
    if 'androidx.compose.ui.platform.LocalConfiguration' not in content:
        content = content.replace('import androidx.compose.ui.platform.LocalContext', 'import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalConfiguration')

    # Find the top of the Composable and add screen dimension vars
    composable_match = re.search(r'@Composable\s*fun [a-zA-Z]+\(.*\)\s*\{', content)
    if composable_match:
        pos = composable_match.end()
        vars_str = "\n    val configuration = LocalConfiguration.current\n    val maxScreenWidth = configuration.screenWidthDp.toFloat()\n    val maxScreenHeight = configuration.screenHeightDp.toFloat()\n"
        content = content[:pos] + vars_str + content[pos:]

    # Replace width value range
    content = re.sub(r'valueRange = 200f\.\.800f,\s*steps = 60', 'valueRange = 200f..maxScreenWidth,\n                                steps = ((maxScreenWidth - 200f) / 10f).toInt()', content)

    # Replace height value range
    content = re.sub(r'valueRange = 300f\.\.1000f,\s*steps = 70', 'valueRange = 300f..maxScreenHeight,\n                                steps = ((maxScreenHeight - 300f) / 10f).toInt()', content)

    with open(filename, 'w') as f:
        f.write(content)

update_file('app/src/main/java/com/example/SidebarSettingsScreen.kt')
update_file('app/src/main/java/com/example/PageCustomizeScreen.kt')

print("Patched.")
