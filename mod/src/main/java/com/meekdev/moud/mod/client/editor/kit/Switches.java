package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;

public final class Switches {

    private static final float TRACK_WIDTH = 34.0f;
    private static final float TRACK_HEIGHT = 18.0f;
    private static final float KNOB_INSET = 3.0f;
    private static final float DISCLOSE_SECONDS = 0.11f;

    private Switches() {
    }

    public static float width() {
        return EditorScale.of(TRACK_WIDTH);
    }

    public static float height() {
        return EditorScale.of(TRACK_HEIGHT);
    }

    public static boolean draw(String id, boolean value) {
        float width = width();
        float height = height();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY() + (ImGui.getFrameHeight() - height) * 0.5f;
        ImGui.setCursorScreenPos(left, top);
        ImGui.invisibleButton(id, width, height);
        boolean hovered = ImGui.isItemHovered();
        float amount = EditorMotion.towards(id + "@on", value ? 1.0f : 0.0f, DISCLOSE_SECONDS);
        paint(left, top, width, height, amount, hovered);
        return ImGui.isItemClicked() != value;
    }

    private static void paint(float left, float top, float width, float height,
                              float amount, boolean hovered) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        int resting = hovered ? EditorStyle.COLOR_FIELD_HOVER : EditorStyle.COLOR_FIELD_BACKGROUND;
        int track = EditorMotion.blend(resting, EditorStyle.COLOR_ACCENT, amount);
        drawList.addRectFilled(left, top, left + width, top + height, track, height * 0.5f);
        float inset = EditorScale.of(KNOB_INSET);
        float knobRadius = height * 0.5f - inset;
        float travelStart = left + inset + knobRadius;
        float travelEnd = left + width - inset - knobRadius;
        int knob = EditorMotion.blend(EditorStyle.COLOR_TEXT_MUTED, EditorStyle.rgb(255, 255, 255), amount);
        drawList.addCircleFilled(travelStart + (travelEnd - travelStart) * amount,
                top + height * 0.5f, knobRadius, knob);
    }
}
