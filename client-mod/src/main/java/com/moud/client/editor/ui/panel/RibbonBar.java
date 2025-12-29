package com.moud.client.editor.ui.panel;

import com.moud.client.editor.ui.EditorMode;
import com.moud.client.editor.ui.SceneEditorOverlay;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;

/**
 * Redesigned ribbon bar with centered mode tabs.
 * The ribbon ONLY contains mode switching - all other tools moved elsewhere.
 */
public final class RibbonBar {
    private static final float RIBBON_HEIGHT = 45f;
    private static final float TAB_WIDTH = 120f;
    private static final float TAB_SPACING = 8f;
    private static final float TAB_BUTTON_HEIGHT = 30f;

    private final SceneEditorOverlay overlay;

    public RibbonBar(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    /**
     * Render the ribbon bar with centered mode tabs.
     */
    public void render() {
        float windowWidth = ImGui.getIO().getDisplaySizeX();

        ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
        ImGui.setNextWindowSize(windowWidth, RIBBON_HEIGHT, ImGuiCond.Always);

        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove |
                    ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar;

        if (ImGui.begin("##RibbonBar", flags)) {
            renderCenteredModeTabs();
        }
        ImGui.end();
    }

    /**
     * Render the mode tabs centered horizontally.
     */
    private void renderCenteredModeTabs() {
        EditorMode currentMode = overlay.getCurrentMode();
        EditorMode[] modes = EditorMode.values();
        int modeCount = modes.length;

        // Calculate total width needed for all tabs
        float totalWidth = (TAB_WIDTH * modeCount) + (TAB_SPACING * (modeCount - 1));

        // Center the tabs horizontally
        float startX = (ImGui.getWindowWidth() - totalWidth) / 2f;
        ImGui.setCursorPosX(startX);

        // Center vertically
        float startY = (RIBBON_HEIGHT - TAB_BUTTON_HEIGHT) / 2f;
        ImGui.setCursorPosY(startY);

        // Render each mode tab
        for (int i = 0; i < modes.length; i++) {
            EditorMode mode = modes[i];
            boolean isActive = (mode == currentMode);

            // Push active style if selected
            if (isActive) {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.5f, 0.8f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.35f, 0.55f, 0.85f, 1.0f);
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.25f, 0.45f, 0.75f, 1.0f);
            }

            // TODO: Add icon before text when icons are available
            // For now, use emoji placeholder + text
            String icon = getIconPlaceholder(mode);
            String label = icon + " " + mode.getDisplayName();

            if (ImGui.button(label, TAB_WIDTH, TAB_BUTTON_HEIGHT)) {
                overlay.setMode(mode);
            }

            if (isActive) {
                ImGui.popStyleColor(3);
            }

            // Add spacing between buttons (except after last one)
            if (i < modes.length - 1) {
                ImGui.sameLine(0, TAB_SPACING);
            }
        }
    }

    /**
     * Get emoji placeholder for mode icon (until real icons are added).
     */
    private String getIconPlaceholder(EditorMode mode) {
        return switch (mode) {
            case SCENE -> "🏠";
            case ANIMATION -> "🎬";
            case BLUEPRINT -> "📐";
            case PLAY -> "▶";
        };
    }

    /**
     * Get the height of the ribbon bar.
     */
    public static float getRibbonHeight() {
        return RIBBON_HEIGHT;
    }
}
