package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.type.ImString;

public final class SearchField {

    private static final float CLEAR_BUTTON_WIDTH = 18.0f;
    private static final float GLYPH_INSET = 5.0f;
    private static final float GLYPH_THICKNESS = 1.6f;

    private SearchField() {
    }

    public static boolean render(String id, String hint, ImString text, float width) {
        float clearWidth = EditorScale.of(CLEAR_BUTTON_WIDTH);
        ImGui.setNextItemWidth(width - clearWidth);
        boolean changed = ImGui.inputTextWithHint(id, hint, text);
        FocusRing.aroundLastItem(id);
        ImGui.sameLine(0.0f, 0.0f);
        return renderClearButton(id, text, clearWidth) || changed;
    }

    private static boolean renderClearButton(String id, ImString text, float width) {
        float height = ImGui.getFrameHeight();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        boolean empty = text.get().isEmpty();
        boolean clicked = ImGui.invisibleButton(id + "-clear", width, height) && !empty;
        float emphasis = EditorMotion.towards(id + "-clear", !empty && ImGui.isItemHovered());
        if (empty) {
            paintMagnifier(left, top, width, height);
        } else {
            paintCross(left, top, width, height, emphasis);
        }
        if (clicked) {
            text.set("");
        }
        return clicked;
    }

    private static void paintMagnifier(float left, float top, float width, float height) {
        float centerX = left + width * 0.5f;
        float centerY = top + height * 0.5f;
        float size = Math.min(width, height) - EditorScale.of(GLYPH_INSET) * 2.0f;
        float radius = size * 0.34f;
        float offset = size * 0.1f;
        float thickness = EditorScale.of(GLYPH_THICKNESS);
        ImGui.getWindowDrawList().addCircle(centerX - offset, centerY - offset, radius,
                EditorStyle.COLOR_TEXT_FAINT, 0, thickness);
        float diagonal = radius * 0.72f;
        ImGui.getWindowDrawList().addLine(centerX - offset + diagonal, centerY - offset + diagonal,
                centerX + size * 0.42f, centerY + size * 0.42f, EditorStyle.COLOR_TEXT_FAINT, thickness);
    }

    private static void paintCross(float left, float top, float width, float height, float emphasis) {
        float half = (Math.min(width, height) - EditorScale.of(GLYPH_INSET) * 2.0f) * 0.5f;
        float centerX = left + width * 0.5f;
        float centerY = top + height * 0.5f;
        float thickness = EditorScale.of(GLYPH_THICKNESS);
        int color = EditorMotion.blend(EditorStyle.COLOR_TEXT_MUTED, EditorStyle.COLOR_TEXT, emphasis);
        ImGui.getWindowDrawList().addLine(centerX - half, centerY - half,
                centerX + half, centerY + half, color, thickness);
        ImGui.getWindowDrawList().addLine(centerX + half, centerY - half,
                centerX - half, centerY + half, color, thickness);
    }
}
