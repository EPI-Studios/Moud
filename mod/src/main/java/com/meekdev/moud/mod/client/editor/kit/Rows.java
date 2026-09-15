package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import imgui.ImGui;

public final class Rows {

    private static final float LABEL_COLUMN_DESIGN_WIDTH = 150.0f;
    private static final float MINIMUM_CONTROL_WIDTH = 60.0f;
    private static final float MINIMUM_LABEL_WIDTH = 72.0f;
    private static final float SPLIT_RATIO = 0.5f;

    private Rows() {
    }

    public static float labelColumnWidth() {
        return EditorScale.of(LABEL_COLUMN_DESIGN_WIDTH);
    }

    public static void of(String label, Runnable control) {
        beginRow(label, splitColumnWidth());
        ImGui.setNextItemWidth(controlWidth());
        control.run();
        ImGui.popID();
    }

    public static float splitColumnWidth() {
        float startX = ImGui.getCursorPosX();
        float available = ImGui.getContentRegionAvailX();
        float control = Math.max(EditorScale.of(MINIMUM_CONTROL_WIDTH), available * SPLIT_RATIO);
        return startX + Math.max(available - control, EditorScale.of(MINIMUM_LABEL_WIDTH));
    }

    public static void of(String label, float columnWidth, Runnable control) {
        beginRow(label, columnWidth);
        ImGui.setNextItemWidth(controlWidth());
        control.run();
        ImGui.popID();
    }

    public static boolean toggle(String label, boolean value) {
        return toggle(label, splitColumnWidth(), value);
    }

    public static boolean toggle(String label, float columnWidth, boolean value) {
        beginRow(label, columnWidth);
        boolean updated = Switches.draw("##value", value);
        ImGui.popID();
        return updated;
    }

    public static void readOnly(String label, String value) {
        beginRow(label, splitColumnWidth());
        Texts.muted(value);
        ImGui.popID();
    }

    private static void beginRow(String label, float columnWidth) {
        ImGui.pushID(label);
        ImGui.alignTextToFramePadding();
        ImGui.textUnformatted(label);
        ImGui.sameLine(columnWidth);
    }

    private static float controlWidth() {
        float available = ImGui.getContentRegionAvailX();
        return available > EditorScale.of(MINIMUM_CONTROL_WIDTH) ? -1.0f : EditorScale.of(MINIMUM_CONTROL_WIDTH);
    }
}
