package com.meekdev.moud.mod.client.editor.animation;

import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiSelectableFlags;
import org.jspecify.annotations.Nullable;

final class ListRow {

    private static final float SELECTED_ALPHA = 0.16f;
    private static final float HOVER_ALPHA = 0.5f;
    private static final float MARKER_WIDTH = 2.0f;
    private static final float MARKER_INSET = 2.0f;
    private static final float LABEL_INSET = 6.0f;
    private static final float GUIDE_OFFSET = 6.0f;
    private static final int GUIDE = EditorStyle.rgba(255, 255, 255, 26);

    private ListRow() {}

    static boolean draw(IconWidgets icons, String id, int depth, @Nullable EditorIcon icon, String label, String trailing,
                        boolean selected, boolean strong, int trailingColor) {
        float height = ImGui.getTextLineHeightWithSpacing() + EditorScale.of(4);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float width = ImGui.getContentRegionAvailX();
        ImGui.pushStyleColor(ImGuiCol.Header, 0);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0);
        boolean clicked = ImGui.selectable("##" + id, selected, ImGuiSelectableFlags.AllowDoubleClick, width, height);
        ImGui.popStyleColor(3);
        boolean hovered = ImGui.isItemHovered();
        ImDrawList draw = ImGui.getWindowDrawList();
        int fill = selected ? EditorStyle.withAlpha(EditorStyle.COLOR_ACCENT, SELECTED_ALPHA)
                : EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_HOVER, hovered ? HOVER_ALPHA : 0.0f);
        draw.addRectFilled(left, top, left + width, top + height, fill, EditorStyle.frameRounding());
        if (selected) {
            draw.addRectFilled(left, top + MARKER_INSET, left + EditorScale.ofAtLeastOne(MARKER_WIDTH), top + height - MARKER_INSET, EditorStyle.COLOR_ACCENT);
        }
        float indent = EditorStyle.indentSpacing();
        for (int level = 0; level < depth; level++) {
            float x = left + level * indent + EditorScale.of(GUIDE_OFFSET) + EditorScale.of(LABEL_INSET);
            draw.addLine(x, top, x, top + height, GUIDE);
        }
        float x = left + EditorScale.of(LABEL_INSET) + depth * indent;
        float iconSize = EditorStyle.iconSizeSmall();
        if (icon != null) {
            float iconTop = top + (height - iconSize) * 0.5f;
            draw.addImage(icons.textureId(icon), x, iconTop, x + iconSize, iconTop + iconSize);
            x += iconSize + EditorScale.of(6);
        }
        int color = selected || strong ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_MUTED;
        draw.addText(x, top + (height - ImGui.getTextLineHeight()) * 0.5f, color, label);
        if (!trailing.isEmpty()) {
            float trailingWidth = Paint.monoWidth(trailing);
            Paint.mono(draw, left + width - trailingWidth - EditorScale.of(8), top + (height - Paint.monoSize()) * 0.5f, trailingColor, trailing);
        }
        return clicked;
    }
}
