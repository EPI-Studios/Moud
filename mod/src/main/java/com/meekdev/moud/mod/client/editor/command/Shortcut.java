package com.meekdev.moud.mod.client.editor.command;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import java.util.Locale;
import net.minecraft.client.input.InputQuirks;
import org.jspecify.annotations.Nullable;

public record Shortcut(boolean ctrl, boolean shift, boolean alt, int key, String label) {

    private static final String CTRL = InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? "Cmd+" : "Ctrl+";

    public static String ctrlLabel(String rest) {
        return CTRL + rest;
    }

    public static Shortcut ctrl(int key, String name) {
        return new Shortcut(true, false, false, key, CTRL + name);
    }

    public static Shortcut ctrlShift(int key, String name) {
        return new Shortcut(true, true, false, key, CTRL + "Shift+" + name);
    }

    public static Shortcut alt(int key, String name) {
        return new Shortcut(false, false, true, key, "Alt+" + name);
    }

    public static Shortcut key(int key, String name) {
        return new Shortcut(false, false, false, key, name);
    }

    public static @Nullable Shortcut parse(String text) {
        boolean ctrl = false;
        boolean shift = false;
        boolean alt = false;
        String[] parts = text.split("\\+");
        for (int n = 0; n < parts.length - 1; n++) {
            switch (parts[n].strip().toLowerCase(Locale.ROOT)) {
                case "ctrl", "control", "cmd", "command" -> ctrl = true;
                case "shift" -> shift = true;
                case "alt" -> alt = true;
                default -> {
                    return null;
                }
            }
        }
        String name = parts[parts.length - 1].strip().toUpperCase(Locale.ROOT);
        int key = key(name);
        if (key < 0) return null;
        String label = (ctrl ? CTRL : "") + (shift ? "Shift+" : "") + (alt ? "Alt+" : "") + name;
        return new Shortcut(ctrl, shift, alt, key, label);
    }

    private static int key(String name) {
        if (name.length() == 1 && name.charAt(0) >= 'A' && name.charAt(0) <= 'Z') return ImGuiKey.A + name.charAt(0) - 'A';
        if (name.length() == 1 && name.charAt(0) >= '0' && name.charAt(0) <= '9') return ImGuiKey._0 + name.charAt(0) - '0';
        if (name.matches("F([1-9]|1[0-2])")) return ImGuiKey.F1 + Integer.parseInt(name.substring(1)) - 1;
        return -1;
    }

    public boolean same(Shortcut other) {
        return ctrl == other.ctrl && shift == other.shift && alt == other.alt && key == other.key;
    }

    public boolean pressed() {
        ImGuiIO io = ImGui.getIO();
        return io.getKeyCtrl() == ctrl && io.getKeyShift() == shift && io.getKeyAlt() == alt && ImGui.isKeyPressed(key, false);
    }
}
