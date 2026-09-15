package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImFloat;
import imgui.type.ImString;

public final class Fields {

    private static final float LABEL_GAP = 6.0f;
    private static final float SUFFIX_GAP = 6.0f;
    private static final float UNDERLINE_THICKNESS = 1.0f;
    private static final int PUSHED_COLOR_COUNT = 3;
    private static final int TRANSPARENT = 0;

    private Fields() {
    }

    /**
     * A field carrying its label inside the frame, so the row needs no separate label column.
     */
    public static boolean withInlineLabel(String id, String label, ImString text, float width) {
        float labelWidth = ImGui.calcTextSizeX(label) + EditorScale.of(LABEL_GAP) * 2.0f;
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.getWindowDrawList().addText(left + EditorScale.of(LABEL_GAP),
                top + ImGui.getStyle().getFramePaddingY(), EditorStyle.COLOR_TEXT_MUTED, label);
        ImGui.setCursorScreenPos(left + labelWidth, top);
        ImGui.setNextItemWidth(width - labelWidth);
        boolean changed = ImGui.inputText(id, text);
        FocusRing.aroundLastItem(id);
        return changed;
    }

    /**
     * A numeric field with a right aligned unit, so the unit does not have to live in the label.
     */
    public static boolean withUnit(String id, ImFloat value, String unit, float width) {
        ImGui.setNextItemWidth(width);
        boolean changed = ImGui.inputFloat(id, value);
        FocusRing.aroundLastItem(id);
        float suffixWidth = ImGui.calcTextSizeX(unit);
        ImGui.getWindowDrawList().addText(
                ImGui.getItemRectMaxX() - suffixWidth - EditorScale.of(SUFFIX_GAP),
                ImGui.getItemRectMinY() + ImGui.getStyle().getFramePaddingY(),
                EditorStyle.COLOR_TEXT_MUTED, unit);
        return changed;
    }

    /**
     * A frameless field that only shows a baseline, which lights up on hover and focus.
     */
    public static boolean underlined(String id, String hint, ImString text, float width) {
        ImGui.pushStyleColor(ImGuiCol.FrameBg, EditorStyle.COLOR_FIELD_BACKGROUND);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, EditorStyle.COLOR_FIELD_HOVER);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, EditorStyle.COLOR_FIELD_ACTIVE);
        ImGui.setNextItemWidth(width);
        boolean changed = ImGui.inputTextWithHint(id, hint, text);
        ImGui.popStyleColor(PUSHED_COLOR_COUNT);
        drawBaseline(id);
        return changed;
    }

    private static void drawBaseline(String id) {
        float emphasis = EditorMotion.towards(id + "-underline",
                ImGui.isItemActive() ? 1.0f : ImGui.isItemHovered() ? 0.5f : 0.0f,
                EditorMotion.DEFAULT_DURATION_SECONDS);
        int border = EditorMotion.blend(EditorStyle.COLOR_WIDGET_OUTLINE,
                EditorStyle.COLOR_ACCENT, emphasis);
        ImGui.getWindowDrawList().addRect(ImGui.getItemRectMinX(), ImGui.getItemRectMinY(),
                ImGui.getItemRectMaxX(), ImGui.getItemRectMaxY(), border,
                EditorStyle.frameRounding(), 0, EditorScale.ofAtLeastOne(UNDERLINE_THICKNESS));
    }
}
