package com.meekdev.moud.mod.client.editor.kit;

import com.meekdev.moud.mod.client.editor.style.EditorScale;
import com.meekdev.moud.mod.client.editor.style.EditorStyle;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;

public final class Dialogs {

    private static final float PADDING = 14.0f;
    private static final float GAP = 8.0f;
    private static final float BUTTON_WIDTH = 96.0f;
    private static final int ACCENT_COLOR_COUNT = 3;
    private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoTitleBar
            | ImGuiWindowFlags.NoCollapse
            | ImGuiWindowFlags.NoResize;

    private Dialogs() {
    }

    public static boolean begin(String identifier, float width) {
        return begin(identifier, width, 0.0f);
    }

    public static boolean begin(String identifier, float width, float height) {
        center(width, height);
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, EditorScale.of(PADDING), EditorScale.of(PADDING));
        boolean open = ImGui.beginPopupModal(identifier, WINDOW_FLAGS);
        ImGui.popStyleVar();
        return open;
    }

    public static void end() {
        ImGui.endPopup();
    }

    public static void title(String text) {
        Sections.title(text);
        gap();
    }

    public static void gap() {
        ImGui.dummy(0.0f, EditorScale.of(GAP));
    }

    public static void alignFooter(int buttonCount) {
        float spacing = ImGui.getStyle().getItemSpacingX() * (buttonCount - 1);
        float buttons = buttonWidth() * buttonCount + spacing;
        ImGui.setCursorPosX(ImGui.getCursorPosX()
                + Math.max(0.0f, ImGui.getContentRegionAvailX() - buttons));
    }

    public static boolean button(String label) {
        return ImGui.button(label, buttonWidth(), Toolbars.buttonHeight());
    }

    public static boolean primaryButton(String label, boolean enabled) {
        pushAccent();
        Disabled.push(!enabled);
        boolean pressed = ImGui.button(label, buttonWidth(), Toolbars.buttonHeight());
        Disabled.pop(!enabled);
        ImGui.popStyleColor(ACCENT_COLOR_COUNT);
        return pressed && enabled;
    }

    public static float buttonWidth() {
        return EditorScale.of(BUTTON_WIDTH);
    }

    private static void center(float width, float height) {
        ImGuiViewport viewport = ImGui.getMainViewport();
        ImGui.setNextWindowPos(viewport.getCenterX(), viewport.getCenterY(), ImGuiCond.Appearing,
                0.5f, 0.5f);
        ImGui.setNextWindowSize(EditorScale.of(width), EditorScale.of(height), ImGuiCond.Appearing);
    }

    private static void pushAccent() {
        ImGui.pushStyleColor(ImGuiCol.Button, EditorStyle.COLOR_ACCENT);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, EditorStyle.COLOR_ACCENT_HOVER);
        ImGui.pushStyleColor(ImGuiCol.Text, EditorStyle.COLOR_TEXT_ON_ACCENT);
    }
}
