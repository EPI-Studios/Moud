package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;

public final class Disclosure {

    private static final float TRIANGLE_SCALE = 0.34f;

    private Disclosure() {
    }

    public static boolean arrow(String id, boolean expanded, float size) {
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        boolean clicked = ImGui.invisibleButton(id, size, size);
        paint(left, top, size, expanded, ImGui.isItemHovered());
        return clicked;
    }

    public static void spacer(float size) {
        ImGui.dummy(size, size);
    }

    private static void paint(float left, float top, float size, boolean expanded, boolean hovered) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float centerX = left + size * 0.5f;
        float centerY = top + size * 0.5f;
        float reach = size * TRIANGLE_SCALE;
        int color = hovered ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_MUTED;
        if (expanded) {
            drawList.addTriangleFilled(centerX - reach, centerY - reach * 0.6f,
                    centerX + reach, centerY - reach * 0.6f, centerX, centerY + reach, color);
            return;
        }
        drawList.addTriangleFilled(centerX - reach * 0.6f, centerY - reach,
                centerX - reach * 0.6f, centerY + reach, centerX + reach, centerY, color);
    }
}
