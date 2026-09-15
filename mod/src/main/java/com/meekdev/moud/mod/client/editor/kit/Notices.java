package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCol;

public final class Notices {

    private static final float PADDING = 8.0f;
    private static final float ACCENT_BAR_WIDTH = 3.0f;
    private static final float BACKGROUND_ALPHA = 0.12f;
    private static final float CORNER_ROUNDING = 3.0f;

    private Notices() {
    }

    public static void info(String message) {
        render(EditorStyle.COLOR_SYSTEM, message);
    }

    public static void warning(String message) {
        render(EditorStyle.COLOR_WARNING, message);
    }

    public static void danger(String message) {
        render(EditorStyle.COLOR_DANGER, message);
    }

    public static void success(String message) {
        render(EditorStyle.COLOR_SUCCESS, message);
    }

    private static void render(int color, String message) {
        float padding = EditorScale.of(PADDING);
        float barWidth = EditorScale.ofAtLeastOne(ACCENT_BAR_WIDTH);
        float bandWidth = Math.max(ImGui.getContentRegionAvailX(), padding * 2.0f + barWidth);
        float textWidth = Math.max(1.0f, bandWidth - padding * 2.0f - barWidth);
        ImVec2 textSize = ImGui.calcTextSize(message, false, textWidth);
        float bandHeight = textSize.y + padding * 2.0f;
        drawBand(color, bandWidth, bandHeight, barWidth);
        renderMessage(color, message, padding, barWidth, textWidth, bandHeight);
    }

    private static void drawBand(int color, float bandWidth, float bandHeight, float barWidth) {
        float originX = ImGui.getCursorScreenPosX();
        float originY = ImGui.getCursorScreenPosY();
        float rounding = EditorScale.of(CORNER_ROUNDING);
        ImGui.getWindowDrawList().addRectFilled(originX, originY, originX + bandWidth, originY + bandHeight,
                EditorStyle.withAlpha(color, BACKGROUND_ALPHA), rounding);
        ImGui.getWindowDrawList().addRectFilled(originX, originY, originX + barWidth, originY + bandHeight,
                color, rounding);
    }

    private static void renderMessage(int color, String message, float padding, float barWidth,
                                      float textWidth, float bandHeight) {
        float startX = ImGui.getCursorPosX();
        float startY = ImGui.getCursorPosY();
        ImGui.setCursorPos(startX + barWidth + padding, startY + padding);
        ImGui.pushStyleColor(ImGuiCol.Text, color);
        EditorStyle.smallFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.smallFontPixelHeight()));
        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + textWidth);
        ImGui.textUnformatted(message);
        ImGui.popTextWrapPos();
        EditorStyle.smallFont().ifPresent(font -> ImGui.popFont());
        ImGui.popStyleColor();
        ImGui.setCursorPos(startX, startY + bandHeight);
        ImGui.dummy(0.0f, 0.0f);
    }
}
