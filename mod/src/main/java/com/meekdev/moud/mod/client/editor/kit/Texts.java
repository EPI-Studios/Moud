package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

public final class Texts {

    private Texts() {
    }

    public static void plain(String text) {
        ImGui.textUnformatted(text);
    }

    public static void colored(int color, String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, color);
        ImGui.textUnformatted(text);
        ImGui.popStyleColor();
    }

    public static void muted(String text) {
        colored(EditorStyle.COLOR_TEXT_MUTED, text);
    }

    public static void wrapped(String text) {
        ImGui.pushTextWrapPos();
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
    }
}
