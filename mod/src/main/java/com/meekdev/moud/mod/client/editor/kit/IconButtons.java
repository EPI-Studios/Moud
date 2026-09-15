package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorIcon;
import com.meekdev.moud.mod.client.editor.style.IconWidgets;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;

public final class IconButtons {

    private static final float MIRRORED_LEFT_U = 1.0f;
    private static final float MIRRORED_RIGHT_U = 0.0f;

    private IconButtons() {
    }

    public static boolean withLabel(IconWidgets icons, String id, EditorIcon icon, String label,
                                    float width, float height) {
        ImGui.pushID(id);
        boolean clicked = ImGui.button("##" + id, width, height);
        paintContent(icons, icon, label);
        ImGui.popID();
        return clicked;
    }

    public static boolean mirrored(IconWidgets icons, String id, EditorIcon icon, float size) {
        return ImGui.imageButton(id, icons.atlasTextureId(icon), size, size,
                MIRRORED_LEFT_U, 0.0f, MIRRORED_RIGHT_U, 1.0f);
    }

    public static void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }

    private static void paintContent(IconWidgets icons, EditorIcon icon, String label) {
        float minX = ImGui.getItemRectMinX();
        float minY = ImGui.getItemRectMinY();
        float width = ImGui.getItemRectMaxX() - minX;
        float height = ImGui.getItemRectMaxY() - minY;
        float iconSize = EditorStyle.iconSizeMedium();
        float labelWidth = ImGui.calcTextSizeX(label);
        float startX = minX + (width - iconSize - EditorStyle.innerSpacing() - labelWidth) * 0.5f;
        var drawList = ImGui.getWindowDrawList();
        drawList.addImage(icons.atlasTextureId(icon), startX, minY + (height - iconSize) * 0.5f,
                startX + iconSize, minY + (height + iconSize) * 0.5f);
        drawList.addText(startX + iconSize + EditorStyle.innerSpacing(),
                minY + (height - ImGui.getTextLineHeight()) * 0.5f, EditorStyle.COLOR_TEXT, label);
    }
}
