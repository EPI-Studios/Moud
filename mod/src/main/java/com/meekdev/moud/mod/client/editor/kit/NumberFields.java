package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImString;

import java.util.Locale;

public final class NumberFields {

    private static final String[] AXIS_LABELS = {"X", "Y", "Z", "W"};
    private static final String FORMAT = "%.3f";
    private static final float MARGIN = 4.0f;
    private static final float SEPARATION = 4.0f;
    private static final float GRABBER_WIDTH = 4.0f;
    private static final float BAR_THICKNESS = 2.0f;
    private static final float BAR_INSET = 4.0f;
    private static final float DEAD_ZONE_STEPS = 4.0f;
    private static final float DRAG_SPEED = 5.0f;
    private static final float SMALLEST_STEP = 0.001f;
    private static final float NO_RANGE = Float.NaN;
    private static final int EDIT_CAPACITY = 32;

    private static final ImString EDIT_BUFFER = new ImString(EDIT_CAPACITY);

    private static String editingIdentity = "";
    private static String draggingIdentity = "";
    private static boolean focusPending;
    private static float dragAccumulator;
    private static float valueBeforeDrag;

    private NumberFields() {
    }

    public static float scalar(String id, float value, float step, float width) {
        return field(id, value, step, width, 0, "", NO_RANGE, NO_RANGE, false);
    }

    public static float ranged(String id, float value, float step, float width,
                               float minimum, float maximum) {
        return field(id, value, step, width, 0, "", minimum, maximum, false);
    }

    public static boolean pair(String id, float[] values, String[] labels, float[] steps, float width) {
        float gap = EditorScale.ofAtLeastOne(1.0f) * 2.0f;
        float cell = (width - gap) / 2;
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        boolean changed = false;
        for (int index = 0; index < 2; index++) {
            ImGui.setCursorScreenPos(left + (cell + gap) * index, top);
            float updated = field(id + "#" + index, values[index], steps[index], cell,
                    index == 0 ? EditorStyle.COLOR_ACCENT : EditorStyle.COLOR_TEXT_MUTED, labels[index], NO_RANGE, NO_RANGE, true);
            changed |= Float.compare(updated, values[index]) != 0;
            values[index] = updated;
        }
        ImGui.setCursorScreenPos(left, top);
        ImGui.dummy(width, ImGui.getFrameHeight());
        return changed;
    }

    public static boolean vector(String id, float[] values, int count, float width, float step) {
        float gap = EditorScale.ofAtLeastOne(1.0f) * 2.0f;
        float cell = (width - gap * (count - 1)) / count;
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        boolean changed = false;
        for (int index = 0; index < count; index++) {
            ImGui.setCursorScreenPos(left + (cell + gap) * index, top);
            float updated = field(id + "#" + index, values[index], step, cell,
                    axisColor(index), AXIS_LABELS[index], NO_RANGE, NO_RANGE, true);
            changed |= Float.compare(updated, values[index]) != 0;
            values[index] = updated;
        }
        ImGui.setCursorScreenPos(left, top);
        ImGui.dummy(width, ImGui.getFrameHeight());
        return changed;
    }

    static String display(float value) {
        String text = String.format(Locale.ROOT, FORMAT, value);
        if (text.indexOf('.') >= 0) text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text.equals("-0") ? "0" : text;
    }

    private static int axisColor(int index) {
        return switch (index) {
            case 0 -> EditorStyle.COLOR_AXIS_X;
            case 1 -> EditorStyle.COLOR_AXIS_Y;
            case 2 -> EditorStyle.COLOR_AXIS_Z;
            default -> EditorStyle.COLOR_ACCENT;
        };
    }

    private static float field(String id, float value, float step, float width, int tint,
                               String label, float minimum, float maximum, boolean flat) {
        if (id.equals(editingIdentity)) {
            return drawEditor(id, value, width, label);
        }
        float height = ImGui.getFrameHeight();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.invisibleButton(id, width, height);
        boolean hovered = ImGui.isItemHovered();
        float updated = dragged(id, value, step, hovered);
        paint(left, top, width, height, updated, tint, label, minimum, maximum, flat, hovered);
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
        return updated;
    }

    private static float dragged(String id, float value, float step, boolean hovered) {
        if (ImGui.isItemClicked()) {
            draggingIdentity = id;
            dragAccumulator = 0.0f;
            valueBeforeDrag = value;
        }
        if (!id.equals(draggingIdentity)) {
            return value;
        }
        if (!ImGui.isItemActive()) {
            return releaseDrag(id, value);
        }
        float speed = ImGui.getIO().getKeyShift() ? DRAG_SPEED * 0.1f : DRAG_SPEED;
        dragAccumulator += ImGui.getIO().getMouseDeltaX() * speed;
        return crossedDeadZone()
                ? valueBeforeDrag + Math.max(step, SMALLEST_STEP) * dragAccumulator
                : value;
    }

    private static boolean crossedDeadZone() {
        return Math.abs(dragAccumulator) > DEAD_ZONE_STEPS * DRAG_SPEED;
    }

    private static float releaseDrag(String id, float value) {
        draggingIdentity = "";
        if (crossedDeadZone()) {
            return value;
        }
        editingIdentity = id;
        focusPending = true;
        EDIT_BUFFER.set(display(value));
        return value;
    }

    private static void paint(float left, float top, float width, float height, float value,
                              int tint, String label, float minimum, float maximum,
                              boolean flat, boolean hovered) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        int fill = hovered ? EditorStyle.COLOR_FIELD_HOVER : EditorStyle.COLOR_FIELD_BACKGROUND;
        drawList.addRectFilled(left, top, left + width, top + height, fill,
                EditorStyle.frameRounding());
        if (!flat) {
            drawList.addRect(left, top, left + width, top + height,
                    EditorStyle.COLOR_WIDGET_OUTLINE, EditorStyle.frameRounding(), 0,
                    EditorScale.ofAtLeastOne(1.0f));
        }
        float textStart = left + EditorScale.of(MARGIN) + paintLabel(drawList, left, top, height, tint, label);
        paintValue(drawList, textStart, top, left + width, height, value);
        paintValueBar(drawList, left, top, width, height, value, minimum, maximum);
    }

    private static float paintLabel(ImDrawList drawList, float left, float top, float height,
                                    int tint, String label) {
        if (label.isEmpty()) {
            return 0.0f;
        }
        float margin = EditorScale.of(MARGIN);
        drawList.addText(left + margin * 2.0f, top + (height - ImGui.getTextLineHeight()) * 0.5f,
                EditorStyle.withAlpha(tint, 0.85f), label);
        return ImGui.calcTextSizeX(label) + margin + EditorScale.of(SEPARATION);
    }

    private static void paintValue(ImDrawList drawList, float textStart, float top, float right,
                                   float height, float value) {
        String text = display(value);
        drawList.pushClipRect(textStart, top, right - EditorScale.of(MARGIN), top + height, true);
        drawList.addText(textStart, top + (height - ImGui.getTextLineHeight()) * 0.5f,
                EditorStyle.COLOR_TEXT, text);
        drawList.popClipRect();
    }

    private static void paintValueBar(ImDrawList drawList, float left, float top, float width,
                                      float height, float value, float minimum, float maximum) {
        if (Float.isNaN(minimum) || Float.isNaN(maximum) || maximum <= minimum) {
            return;
        }
        float margin = EditorScale.of(MARGIN);
        float grabber = EditorScale.of(GRABBER_WIDTH);
        float span = width - margin * 2.0f - grabber;
        float barY = top + height - EditorScale.of(BAR_INSET);
        float thickness = EditorScale.ofAtLeastOne(BAR_THICKNESS);
        float ratio = Math.clamp((value - minimum) / (maximum - minimum), 0.0f, 1.0f);
        drawList.addRectFilled(left + margin, barY, left + margin + span, barY + thickness,
                EditorStyle.withAlpha(EditorStyle.COLOR_TEXT, 0.2f));
        drawList.addRectFilled(left + margin, barY, left + margin + span * ratio, barY + thickness,
                EditorStyle.withAlpha(EditorStyle.COLOR_TEXT, 0.45f));
        drawList.addRectFilled(left + margin + span * ratio, barY - thickness * 0.5f,
                left + margin + span * ratio + grabber, barY + thickness * 1.5f,
                EditorStyle.withAlpha(EditorStyle.COLOR_TEXT, 0.9f));
    }

    private static float drawEditor(String id, float value, float width, String label) {
        float height = ImGui.getFrameHeight();
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(left, top, left + width, top + height,
                EditorStyle.COLOR_FIELD_ACTIVE, EditorStyle.frameRounding());
        pushEditorStyle(label);
        ImGui.setNextItemWidth(width);
        if (focusPending) {
            ImGui.setKeyboardFocusHere();
            focusPending = false;
        }
        boolean submitted = ImGui.inputText("##" + id, EDIT_BUFFER,
                ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll);
        boolean closed = submitted || ImGui.isItemDeactivated();
        ImGui.popStyleColor(3);
        ImGui.popStyleVar();
        return closed ? finishEditing(value) : value;
    }

    private static void pushEditorStyle(String label) {
        float leading = EditorScale.of(MARGIN) + (label.isEmpty() ? 0.0f : EditorScale.of(16.0f));
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, leading, EditorStyle.framePaddingY());
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0);
    }

    private static float finishEditing(float value) {
        editingIdentity = "";
        try {
            return Float.parseFloat(EDIT_BUFFER.get().trim());
        } catch (NumberFormatException ignored) {
            return value;
        }
    }
}
