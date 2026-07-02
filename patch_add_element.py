import re

def update_add_element(filename):
    with open(filename, 'r') as f:
        content = f.read()

    # Add ActionType
    content = content.replace('enum class ActionType {\n    APP, SHORTCUT, FOLDER, LINK, EMPTY_ITEM, INTENT,\n    SYSTEM, VOLUME, MEDIA, BRIGHTNESS, SCREEN_TIMEOUT, SCREEN_ORIENTATION, WIDGET,\n    SPECIFIC_SYSTEM_ACTION\n}', 'enum class ActionType {\n    APP, SHORTCUT, FOLDER, LINK, EMPTY_ITEM, INTENT,\n    SYSTEM, VOLUME, MEDIA, BRIGHTNESS, SCREEN_TIMEOUT, SCREEN_ORIENTATION, WIDGET, SETTINGS_SHORTCUT_HEADER,\n    SPECIFIC_SYSTEM_ACTION, SPECIFIC_SETTINGS_SHORTCUT\n}')

    # Add ALL_SETTINGS_SHORTCUTS import/usage
    # In Mode enum, add SETTINGS_SHORTCUTS
    content = content.replace('enum class Mode { MAIN, SYSTEM_ACTIONS, VOLUME_ACTIONS, MEDIA_ACTIONS, DISPLAY_ACTIONS }', 'enum class Mode { MAIN, SYSTEM_ACTIONS, VOLUME_ACTIONS, MEDIA_ACTIONS, DISPLAY_ACTIONS, SETTINGS_SHORTCUTS }')

    # Add option in Mode.MAIN
    add_option = """
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_preferences, "Android Settings Shortcut", "(${ALL_SETTINGS_SHORTCUTS.size})", ActionType.SETTINGS_SHORTCUT_HEADER))
            items.add(AddElementItem.Action(android.R.drawable.ic_menu_info_details, "System", "(${ALL_SYSTEM_ACTIONS.size})", ActionType.SYSTEM))
"""
    content = content.replace('items.add(AddElementItem.Action(android.R.drawable.ic_menu_info_details, "System", "(${ALL_SYSTEM_ACTIONS.size})", ActionType.SYSTEM))', add_option.strip())

    # Add sub-menu population
    sub_menu = """
        } else if (currentMode == Mode.SETTINGS_SHORTCUTS) {
            items.add(AddElementItem.Header("Settings Shortcuts"))
            for (action in ALL_SETTINGS_SHORTCUTS) {
                items.add(AddElementItem.Action(action.iconResId, action.label, "", ActionType.SPECIFIC_SETTINGS_SHORTCUT, action.id))
            }
        } else if (currentMode == Mode.SYSTEM_ACTIONS) {
"""
    content = content.replace('} else if (currentMode == Mode.SYSTEM_ACTIONS) {', sub_menu.strip())

    # Handle action click
    handle_action = """
            ActionType.SETTINGS_SHORTCUT_HEADER -> {
                currentMode = Mode.SETTINGS_SHORTCUTS
                loadData()
                updateHeaderTitle("Settings")
            }
            ActionType.SPECIFIC_SETTINGS_SHORTCUT -> {
                addSidebarItem(item.id)
                close()
            }
            ActionType.SYSTEM -> {
"""
    content = content.replace('ActionType.SYSTEM -> {', handle_action.strip())

    with open(filename, 'w') as f:
        f.write(content)

update_add_element('app/src/main/java/com/example/service/AddElementOverlayView.kt')
print("Patched AddElementOverlayView.")
