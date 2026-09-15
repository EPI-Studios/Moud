package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;

import java.util.List;

public final class SegmentedControl {

    private static final float PADDING_X = 12.0f;
    private static final float PADDING_Y = 4.0f;
    private static final float ROUNDING = 4.0f;
    private static final float SELECTION_INSET = 2.0f;
    private static final float PILL_TRAVEL_SECONDS = 0.11f;

    private SegmentedControl() {
    }

    public static float width(List<String> labels) {
        return labels.isEmpty() ? 0.0f : segmentWidth(labels) * labels.size();
    }

    public static float height() {
        return ImGui.getTextLineHeight() + EditorScale.of(PADDING_Y) * 2.0f;
    }

    public static int render(String id, List<String> labels, int selectedIndex) {
        if (labels.isEmpty()) {
            return selectedIndex;
        }
        float height = height();
        float segmentWidth = segmentWidth(labels);
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        float rounding = EditorScale.of(ROUNDING);
        ImGui.getWindowDrawList().addRectFilled(left, top, left + segmentWidth * labels.size(), top + height,
                EditorStyle.COLOR_SUNKEN_BACKGROUND, rounding);
        paintSelectionPill(id, selectedIndex, left, top, segmentWidth, height, rounding);
        int chosen = renderSegments(id, labels, selectedIndex, left, top, segmentWidth, height, rounding);
        ImGui.dummy(segmentWidth * labels.size(), height);
        return chosen;
    }

    private static void paintSelectionPill(String id, int selectedIndex, float left, float top,
                                           float width, float height, float rounding) {
        float position = EditorMotion.towards(id + "-pill", selectedIndex, PILL_TRAVEL_SECONDS);
        float inset = EditorScale.of(SELECTION_INSET);
        float pillLeft = left + width * position + inset;
        ImGui.getWindowDrawList().addRectFilled(pillLeft, top + inset,
                pillLeft + width - inset * 2.0f, top + height - inset,
                EditorStyle.COLOR_WIDGET_ACTIVE, rounding);
    }

    private static int renderSegments(String id, List<String> labels, int selectedIndex,
                                      float left, float top, float width, float height, float rounding) {
        int chosen = selectedIndex;
        for (int index = 0; index < labels.size(); index++) {
            float segmentLeft = left + width * index;
            ImGui.setCursorScreenPos(segmentLeft, top);
            if (ImGui.invisibleButton(id + "-segment-" + index, width, height)) {
                chosen = index;
            }
            paintSegment(id, labels.get(index), index, index == selectedIndex,
                    corners(index, labels.size()), segmentLeft, top, width, height, rounding);
        }
        ImGui.setCursorScreenPos(left, top);
        return chosen;
    }

    private static void paintSegment(String id, String label, int index, boolean selected, int corners,
                                     float left, float top, float width, float height, float rounding) {
        String motionId = id + "-segment-" + index;
        float emphasis = EditorMotion.towards(motionId, !selected && ImGui.isItemHovered());
        ImDrawList drawList = ImGui.getWindowDrawList();
        if (emphasis > 0.0f) {
            float inset = EditorScale.of(SELECTION_INSET);
            drawList.addRectFilled(left + inset, top + inset, left + width - inset, top + height - inset,
                    EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_HOVER, emphasis * 0.7f),
                    rounding, corners);
        }
        float textX = left + (width - ImGui.calcTextSizeX(label)) * 0.5f;
        float textY = top + (height - ImGui.getTextLineHeight()) * 0.5f;
        drawList.addText(textX, textY,
                selected ? EditorStyle.COLOR_TEXT : EditorStyle.COLOR_TEXT_MUTED, label);
    }

    private static int corners(int index, int count) {
        if (count == 1) {
            return ImDrawFlags.RoundCornersAll;
        }
        if (index == 0) {
            return ImDrawFlags.RoundCornersLeft;
        }
        if (index == count - 1) {
            return ImDrawFlags.RoundCornersRight;
        }
        return ImDrawFlags.RoundCornersNone;
    }

    private static float segmentWidth(List<String> labels) {
        return widestLabel(labels) + EditorScale.of(PADDING_X) * 2.0f;
    }

    private static float widestLabel(List<String> labels) {
        float widest = 0.0f;
        for (String label : labels) {
            widest = Math.max(widest, ImGui.calcTextSizeX(label));
        }
        return widest;
    }
}
