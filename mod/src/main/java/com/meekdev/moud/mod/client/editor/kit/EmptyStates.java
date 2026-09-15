package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.flag.ImGuiCol;
import imgui.ImGui;

import java.util.List;

public final class EmptyStates {

    private static final float LINE_GAP = 6.0f;
    private static final float TITLE_GAP = 10.0f;

    private EmptyStates() {
    }

    public static void centered(String title, List<String> hints) {
        centerVertically(estimatedHeight(hints.size()));
        centerTitle(title);
        ImGui.dummy(0.0f, EditorScale.of(TITLE_GAP));
        EditorStyle.smallFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.smallFontPixelHeight()));
        for (String hint : hints) {
            centerText(hint);
            ImGui.dummy(0.0f, EditorScale.of(LINE_GAP));
        }
        EditorStyle.smallFont().ifPresent(font -> ImGui.popFont());
    }

    private static void centerTitle(String text) {
        EditorStyle.titleFont().ifPresent(font -> ImGui.pushFont(font, EditorStyle.titleFontPixelHeight()));
        float indent = (ImGui.getContentRegionAvailX() - ImGui.calcTextSize(text).x) * 0.5f;
        if (indent > 0.0f) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + indent);
        }
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_MUTED);
        ImGui.textUnformatted(text);
        ImGui.popStyleColor();
        EditorStyle.titleFont().ifPresent(font -> ImGui.popFont());
    }

    private static float estimatedHeight(int hintCount) {
        float lineHeight = ImGui.getTextLineHeightWithSpacing();
        return lineHeight * (hintCount + 1) + EditorScale.of(TITLE_GAP)
                + EditorScale.of(LINE_GAP) * hintCount;
    }

    private static void centerVertically(float blockHeight) {
        float free = ImGui.getContentRegionAvailY() - blockHeight;
        if (free > 0.0f) {
            ImGui.dummy(0.0f, free * 0.5f);
        }
    }

    public static void centerText(String text) {
        float indent = (ImGui.getContentRegionAvailX() - ImGui.calcTextSize(text).x) * 0.5f;
        if (indent > 0.0f) {
            ImGui.setCursorPosX(ImGui.getCursorPosX() + indent);
        }
        Texts.muted(text);
    }
}
