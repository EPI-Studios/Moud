package com.meekdev.moud.mod.client.editor.command;

import imgui.ImGui;
import imgui.ImGuiIO;

public record Shortcut(boolean ctrl, boolean shift, boolean alt, int key, String label) {

    public static Shortcut ctrl(int key, String name) {
        return new Shortcut(true, false, false, key, "Ctrl+" + name);
    }

    public static Shortcut ctrlShift(int key, String name) {
        return new Shortcut(true, true, false, key, "Ctrl+Shift+" + name);
    }

    public static Shortcut alt(int key, String name) {
        return new Shortcut(false, false, true, key, "Alt+" + name);
    }

    public static Shortcut key(int key, String name) {
        return new Shortcut(false, false, false, key, name);
    }

    public boolean pressed() {
        ImGuiIO io = ImGui.getIO();
        return io.getKeyCtrl() == ctrl && io.getKeyShift() == shift && io.getKeyAlt() == alt && ImGui.isKeyPressed(key, false);
    }
}
