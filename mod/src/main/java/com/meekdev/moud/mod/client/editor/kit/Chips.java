package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;

public final class Chips {

    private static final float PADDING_X = 6.0f;
    private static final float PADDING_Y = 1.0f;
    private static final float ROUNDING = 3.0f;
    private static final float BACKGROUND_ALPHA = 0.18f;

    private Chips() {
    }

    public static void draw(String label, int color) {
        float paddingX = EditorScale.of(PADDING_X);
        float paddingY = EditorScale.of(PADDING_Y);
        float width = ImGui.calcTextSizeX(label) + paddingX * 2.0f;
        float height = ImGui.getTextLineHeight() + paddingY * 2.0f;
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.getWindowDrawList().addRectFilled(left, top, left + width, top + height,
                EditorStyle.withAlpha(color, BACKGROUND_ALPHA), EditorStyle.frameRounding());
        ImGui.getWindowDrawList().addText(left + paddingX, top + paddingY, color, label);
        ImGui.dummy(width, height);
    }

    public static void drawInline(String label, int color) {
        draw(label, color);
        ImGui.sameLine();
    }
}
