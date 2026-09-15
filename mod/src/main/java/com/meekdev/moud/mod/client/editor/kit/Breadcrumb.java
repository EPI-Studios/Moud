package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.List;
import java.util.OptionalInt;

public final class Breadcrumb {

    private static final String SEPARATOR = "/";
    private static final int TRANSPARENT = 0;
    private static final int PUSHED_COLOR_COUNT = 4;

    private Breadcrumb() {
    }

    public static OptionalInt render(String id, List<String> segments) {
        OptionalInt clicked = OptionalInt.empty();
        for (int index = 0; index < segments.size(); index++) {
            if (index > 0) {
                renderSeparator();
            }
            if (renderSegment(id, segments.get(index), index, index == segments.size() - 1)) {
                clicked = OptionalInt.of(index);
            }
        }
        return clicked;
    }

    private static void renderSeparator() {
        ImGui.sameLine();
        Texts.colored(EditorStyle.COLOR_TEXT_MUTED, SEPARATOR);
        ImGui.sameLine();
    }

    private static boolean renderSegment(String id, String segment, int index, boolean last) {
        String motionId = id + "-crumb-" + index;
        int color = last
                ? EditorStyle.COLOR_TEXT
                : EditorMotion.blend(EditorStyle.COLOR_TEXT_MUTED, EditorStyle.COLOR_ACCENT_HOVER,
                        EditorMotion.valueOf(motionId));
        ImGui.pushStyleColor(ImGuiCol.Text, color);
        ImGui.pushStyleColor(ImGuiCol.Button, TRANSPARENT);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, TRANSPARENT);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, TRANSPARENT);
        boolean clicked = ImGui.button(segment + "##" + motionId);
        ImGui.popStyleColor(PUSHED_COLOR_COUNT);
        EditorMotion.towards(motionId, !last && ImGui.isItemHovered());
        return clicked && !last;
    }
}
