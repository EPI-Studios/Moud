package com.meekdev.moud.mod.client.editor.assets;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import java.util.Locale;

final class ExtensionBadge {

    private static final int[] PALETTE = {
            EditorStyle.rgb(94, 129, 172), EditorStyle.rgb(163, 190, 140), EditorStyle.rgb(208, 135, 112),
            EditorStyle.rgb(180, 142, 173), EditorStyle.rgb(143, 188, 187), EditorStyle.rgb(235, 203, 139)};
    private static final int MAXIMUM_LETTERS = 4;
    private static final float PADDING_X = 3.0f;
    private static final float PADDING_Y = 1.0f;
    private static final float ROUNDING = 2.0f;
    private static final float BOTTOM_GAP = 4.0f;
    private static final float CORNER_SHARE = 0.45f;

    private ExtensionBadge() {}

    static void draw(String fileName, float iconX, float iconY, float iconSize) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return;
        String label = fileName.substring(dot + 1).toUpperCase(Locale.ROOT);
        if (label.length() > MAXIMUM_LETTERS) label = label.substring(0, MAXIMUM_LETTERS);
        int color = PALETTE[(label.hashCode() & 0x7FFFFFFF) % PALETTE.length];
        ImDrawList draw = ImGui.getWindowDrawList();
        float textWidth = ImGui.calcTextSize(label).x;
        float textHeight = ImGui.getTextLineHeight();
        float width = textWidth + EditorScale.of(PADDING_X) * 2.0f;
        float height = textHeight + EditorScale.of(PADDING_Y) * 2.0f;
        if (width > iconSize || height > iconSize * 0.5f) {
            float corner = iconSize * CORNER_SHARE;
            draw.addRectFilled(iconX + iconSize - corner, iconY + iconSize - corner, iconX + iconSize, iconY + iconSize, color, EditorScale.of(ROUNDING));
            return;
        }
        float left = iconX + (iconSize - width) * 0.5f;
        float top = iconY + iconSize - height - EditorScale.of(BOTTOM_GAP);
        draw.addRectFilled(left, top, left + width, top + height, color, EditorScale.of(ROUNDING));
        draw.addText(left + EditorScale.of(PADDING_X), top + EditorScale.of(PADDING_Y), EditorStyle.COLOR_WINDOW_BACKGROUND, label);
    }
}
