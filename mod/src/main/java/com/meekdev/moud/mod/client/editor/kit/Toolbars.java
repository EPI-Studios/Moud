package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

public final class Toolbars {

    private static final float GROUP_SPACING = 10.0f;
    private static final float ITEM_SPACING = 2.0f;
    private static final float SEPARATOR_HEIGHT = 20.0f;
    private static final float SEPARATOR_INSET = 3.0f;

    private static final int TRANSPARENT = 0;
    private static final int PUSHED_FLAT_COLORS = 3;

    private Toolbars() {
    }

    public static void pushFlatButtons() {
        ImGui.pushStyleColor(ImGuiCol.Button, TRANSPARENT);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, EditorStyle.COLOR_ELEVATED_BACKGROUND);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, EditorStyle.COLOR_WIDGET_BACKGROUND);
    }

    public static void popFlatButtons() {
        ImGui.popStyleColor(PUSHED_FLAT_COLORS);
    }

    public static float buttonHeight() {
        return Math.max(ImGui.getTextLineHeight(), EditorStyle.iconSizeToolbar())
                + EditorStyle.framePaddingY() * 2.0f;
    }

    public static boolean textButton(String label) {
        return ImGui.button(label, 0.0f, buttonHeight());
    }

    public static void nextItem() {
        ImGui.sameLine(0.0f, EditorScale.of(ITEM_SPACING));
    }

    public static void groupSeparator() {
        float spacing = EditorScale.of(GROUP_SPACING);
        ImGui.sameLine(0.0f, spacing);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        float rowHeight = buttonHeight();
        float length = EditorScale.of(SEPARATOR_HEIGHT);
        float top = y + (rowHeight - length) * 0.5f;
        ImGui.getWindowDrawList().addLine(x, top, x, top + length, EditorStyle.COLOR_OUTLINE);
        ImGui.dummy(EditorScale.ofAtLeastOne(1.0f), rowHeight);
        ImGui.sameLine(0.0f, spacing);
    }
}
