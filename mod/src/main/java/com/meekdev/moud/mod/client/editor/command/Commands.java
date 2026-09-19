package com.meekdev.moud.mod.client.editor.command;

import imgui.ImGui;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class Commands {

    private final Map<String, List<EditorCommand>> menus = new LinkedHashMap<>();

    public Commands menu(String name) {
        menus.computeIfAbsent(name, key -> new ArrayList<>());
        return this;
    }

    public Commands add(EditorCommand command) {
        menus.computeIfAbsent(command.menu(), key -> new ArrayList<>()).add(command);
        return this;
    }

    public void renderMenus() {
        for (Map.Entry<String, List<EditorCommand>> menu : menus.entrySet()) {
            if (!ImGui.beginMenu(menu.getKey())) continue;
            for (EditorCommand command : menu.getValue()) {
                if (!command.inMenu()) continue;
                String shortcut = command.shortcut() == null ? "" : command.shortcut().label();
                if (ImGui.menuItem(command.label() + "###" + command.id(), shortcut, false, command.available().getAsBoolean())) {
                    command.action().run();
                }
            }
            ImGui.endMenu();
        }
    }

    public @Nullable EditorCommand using(Shortcut shortcut) {
        for (List<EditorCommand> menu : menus.values()) {
            for (EditorCommand command : menu) {
                if (command.shortcut() != null && command.shortcut().same(shortcut)) return command;
            }
        }
        return null;
    }

    public void handleShortcuts() {
        if (ImGui.getIO().getWantTextInput()) return;
        for (List<EditorCommand> menu : menus.values()) {
            for (EditorCommand command : menu) {
                if (command.shortcut() != null && command.shortcut().pressed()) command.runIfAvailable();
            }
        }
    }
}
