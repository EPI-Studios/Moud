package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

public final class ToggleStyle {

    private static final float HOVER_LIFT = 0.08f;
    private static final int PUSHED_COLOR_COUNT = 4;

    private ToggleStyle() {
    }

    public static void push(boolean active) {
        push(active, 0.0f);
    }

    public static void push(boolean active, float emphasis) {
        if (!active) {
            return;
        }
        int background = EditorStyle.lighten(EditorStyle.COLOR_WIDGET_ACTIVE,
                HOVER_LIFT * Math.clamp(emphasis, 0.0f, 1.0f));
        ImGui.pushStyleColor(ImGuiCol.Button, background);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, EditorStyle.lighten(background, HOVER_LIFT));
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, background);
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_FOCUS);
    }

    public static void pop(boolean active) {
        if (active) {
            ImGui.popStyleColor(PUSHED_COLOR_COUNT);
        }
    }
}
