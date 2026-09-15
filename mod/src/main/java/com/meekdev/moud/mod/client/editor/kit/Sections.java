package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorMotion;
import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiCol;

import java.util.HashMap;
import java.util.Map;

public final class Sections {

    private static final float HEADER_PADDING_Y = 5.0f;
    private static final float OUTER_MARGIN = 2.0f;
    private static final float ARROW_SIZE = 9.0f;
    private static final float ARROW_GAP = 6.0f;
    private static final float HEADER_ALPHA = 0.4f;
    private static final float HOVER_LIFT = 0.2f;
    private static final float PRESS_SINK = 0.05f;
    private static final float DISCLOSE_SECONDS = 0.11f;
    private static final float DIVIDER_SPACING = 4.0f;
    private static final Map<Integer, Boolean> OPEN_STATES = new HashMap<>();

    private Sections() {
    }

    public static boolean header(String label) {
        return header(label, true);
    }

    public static boolean header(String label, long iconTextureId) {
        return header(label, true, iconTextureId);
    }

    public static boolean header(String label, boolean openByDefault) {
        return header(label, openByDefault, 0);
    }

    public static boolean header(String label, boolean openByDefault, long iconTextureId) {
        int key = ImGui.getID(label);
        boolean open = OPEN_STATES.computeIfAbsent(key, ignored -> openByDefault);
        float width = ImGui.getContentRegionAvailX();
        float height = ImGui.getTextLineHeight() + EditorScale.of(HEADER_PADDING_Y) * 2.0f;
        float left = ImGui.getCursorScreenPosX();
        float top = ImGui.getCursorScreenPosY();
        ImGui.setNextItemAllowOverlap();
        ImGui.invisibleButton(label, width, height);
        if (ImGui.isItemClicked()) {
            open = !open;
            OPEN_STATES.put(key, open);
        }
        paintHeader(label, key, open, left, top, width, height, iconTextureId);
        return open;
    }

    private static void paintHeader(String label, int key, boolean open,
                                    float left, float top, float width, float height,
                                    long iconTextureId) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        drawList.addRectFilled(left, top, left + width, top + height, headerFill());
        float arrowX = left + EditorScale.of(OUTER_MARGIN);
        float amount = EditorMotion.towards(key + "@disclose", open ? 1.0f : 0.0f, DISCLOSE_SECONDS);
        paintArrow(drawList, arrowX, top + height * 0.5f, EditorScale.of(ARROW_SIZE), amount);
        float textX = arrowX + EditorScale.of(ARROW_SIZE) + EditorScale.of(ARROW_GAP);
        textX += paintIcon(drawList, iconTextureId, textX, top, height);
        drawList.addText(textX, top + (height - ImGui.getTextLineHeight()) * 0.5f,
                EditorStyle.COLOR_TEXT, label);
    }

    private static float paintIcon(ImDrawList drawList, long iconTextureId, float left,
                                   float top, float height) {
        if (iconTextureId == 0) {
            return 0.0f;
        }
        float size = EditorStyle.iconSizeSmall();
        float iconTop = top + (height - size) * 0.5f;
        drawList.addImage(iconTextureId, left, iconTop, left + size, iconTop + size);
        return size + EditorScale.of(ARROW_GAP);
    }

    private static int headerFill() {
        int resting = EditorStyle.withAlpha(EditorStyle.COLOR_WIDGET_BACKGROUND, HEADER_ALPHA);
        if (ImGui.isItemActive()) {
            return EditorStyle.darken(resting, PRESS_SINK);
        }
        return ImGui.isItemHovered() ? EditorStyle.lighten(resting, HOVER_LIFT) : resting;
    }

    private static void paintArrow(ImDrawList drawList, float left, float centerY,
                                   float size, float amount) {
        float angle = (float) (-Math.PI / 2.0) * (1.0f - Math.clamp(amount, 0.0f, 1.0f));
        float centerX = left + size * 0.5f;
        float half = size * 0.45f;
        drawList.pathClear();
        appendRotated(drawList, centerX, centerY, -half, -half * 0.55f, angle);
        appendRotated(drawList, centerX, centerY, 0.0f, half * 0.55f, angle);
        appendRotated(drawList, centerX, centerY, half, -half * 0.55f, angle);
        drawList.pathStroke(EditorStyle.COLOR_TEXT_MUTED, ImDrawFlags.None,
                EditorScale.ofAtLeastOne(1.4f));
    }

    private static void appendRotated(ImDrawList drawList, float centerX, float centerY,
                                      float x, float y, float angle) {
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        drawList.pathLineTo(centerX + x * cosine - y * sine, centerY + x * sine + y * cosine);
    }

    public static void divider() {
        ImGui.dummy(0.0f, EditorScale.of(DIVIDER_SPACING));
        ImGui.separator();
        ImGui.dummy(0.0f, EditorScale.of(DIVIDER_SPACING));
    }

    public static void caption(String text) {
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
        EditorStyle.smallFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.smallFontPixelHeight()));
        ImGui.textUnformatted(text);
        EditorStyle.smallFont().ifPresent(font -> ImGui.popFont());
        ImGui.popStyleColor();
    }

    public static void title(String text) {
        EditorStyle.titleFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.titleFontPixelHeight()));
        ImGui.textUnformatted(text);
        EditorStyle.titleFont().ifPresent(font -> ImGui.popFont());
    }
}
