package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;

public final class Category {

    private static final float VERTICAL_PADDING = 5.0f;
    private static final float ICON_SEPARATION = 6.0f;

    private Category() {
    }

    public static float height() {
        return ImGui.getTextLineHeight() + EditorScale.of(VERTICAL_PADDING) * 2.0f;
    }

    public static void draw(String title, long iconTextureId) {
        float width = ImGui.getContentRegionAvailX();
        float height = height();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(left, top, left + width, top + height,
                EditorStyle.COLOR_ELEVATED_BACKGROUND);
        drawCentered(drawList, title, iconTextureId, left, top, width, height);
        ImGui.dummy(width, height);
    }

    private static void drawCentered(ImDrawList drawList, String title, long iconTextureId,
                                     float left, float top, float width, float height) {
        float iconSize = iconTextureId == 0 ? 0.0f : EditorStyle.iconSizeSmall();
        float separation = iconSize == 0.0f ? 0.0f : EditorScale.of(ICON_SEPARATION);
        float contentWidth = ImGui.calcTextSizeX(title) + iconSize + separation;
        float start = left + Math.max(0.0f, (width - contentWidth) * 0.5f);
        if (iconSize > 0.0f) {
            float iconTop = top + (height - iconSize) * 0.5f;
            drawList.addImage(iconTextureId, start, iconTop, start + iconSize, iconTop + iconSize);
        }
        drawList.addText(start + iconSize + separation,
                top + (height - ImGui.getTextLineHeight()) * 0.5f, EditorStyle.COLOR_TEXT, title);
    }
}
