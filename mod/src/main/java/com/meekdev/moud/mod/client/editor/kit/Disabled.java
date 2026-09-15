package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

public final class Disabled {

    private static final int PUSHED_COLOR_COUNT = 5;
    private static final float SURFACE_ALPHA = 0.5f;

    private Disabled() {
    }

    public static void push(boolean disabled) {
        if (!disabled) {
            return;
        }
        ImGui.beginDisabled();
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
        ImGui.pushStyleColor(ImGuiCol.FrameBg,
                EditorStyle.withAlpha(EditorStyle.COLOR_FIELD_BACKGROUND, SURFACE_ALPHA));
        ImGui.pushStyleColor(ImGuiCol.Button,
                EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_BACKGROUND, SURFACE_ALPHA));
        ImGui.pushStyleColor(ImGuiCol.CheckMark, EditorStyle.COLOR_TEXT_MUTED);
        ImGui.pushStyleColor(ImGuiCol.SliderGrab, EditorStyle.COLOR_TEXT_MUTED);
    }

    public static void pop(boolean disabled) {
        if (!disabled) {
            return;
        }
        ImGui.popStyleColor(PUSHED_COLOR_COUNT);
        ImGui.endDisabled();
    }
}
