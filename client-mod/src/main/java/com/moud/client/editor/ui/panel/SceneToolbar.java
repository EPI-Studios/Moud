package com.moud.client.editor.ui.panel;

import com.moud.client.editor.assets.EditorAssetCatalog;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.network.MoudPackets;
import imgui.ImGui;
import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.List;

/**
 * Scene mode toolbar - appears below ribbon in SCENE mode only.
 * Replaces the floating ViewportToolbar with an integrated toolbar.
 */
public final class SceneToolbar {
    private static final float TOOLBAR_HEIGHT = 35f;
    private static final float BUTTON_HEIGHT = 25f;
    private static final float BUTTON_WIDTH = 70f;

    private final SceneEditorOverlay overlay;

    public SceneToolbar(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    /**
     * Render the scene toolbar at the given Y offset.
     * @param session Current scene session
     * @param yOffset Y position to render at (usually ribbon height)
     */
    public void render(SceneSessionManager session, float yOffset) {
        float windowWidth = ImGui.getIO().getDisplaySizeX();

        ImGui.setNextWindowPos(0, yOffset, ImGuiCond.Always);
        ImGui.setNextWindowSize(windowWidth, TOOLBAR_HEIGHT, ImGuiCond.Always);

        int flags = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove |
                    ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoScrollbar;

        if (ImGui.begin("##SceneToolbar", flags)) {
            renderToolbarContent(session);
        }
        ImGui.end();
    }

    private void renderToolbarContent(SceneSessionManager session) {
        int operation = overlay.getCurrentGizmoOperation();

        // Add some left padding
        ImGui.setCursorPosX(10);

        // Transform mode buttons
        renderTransformModeButton("Translate", Operation.TRANSLATE, operation);
        ImGui.sameLine();
        renderTransformModeButton("Rotate", Operation.ROTATE, operation);
        ImGui.sameLine();
        renderTransformModeButton("Scale", Operation.SCALE, operation);

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        // Local/World toggle (only for translate/rotate)
        if (operation != Operation.SCALE) {
            int mode = overlay.getGizmoMode();
            renderModeButton("Local", Mode.LOCAL, mode);
            ImGui.sameLine();
            renderModeButton("World", Mode.WORLD, mode);

            ImGui.sameLine();
            ImGui.text(" | ");
            ImGui.sameLine();
        }

        // Snap toggle
        ImBoolean snapToggle = new ImBoolean(overlay.isSnapEnabled());
        if (ImGui.checkbox("Snap", snapToggle)) {
            overlay.setSnapEnabled(snapToggle.get());
        }

        if (overlay.isSnapEnabled()) {
            ImGui.sameLine();
            float[] snapValues = overlay.getSnapValues();
            ImGui.setNextItemWidth(60);

            // Use ImFloat wrapper for single value input
            imgui.type.ImFloat snapValue = new imgui.type.ImFloat(snapValues[0]);
            if (ImGui.inputFloat("##SnapValue", snapValue)) {
                float value = Math.max(0.1f, snapValue.get());
                snapValues[0] = snapValues[1] = snapValues[2] = value;
            }
        }

        // Pivot mode (only show if multi-selection)
        if (overlay.hasMultiSelection()) {
            ImGui.sameLine();
            ImGui.text(" | Pivot:");
            ImGui.sameLine();

            SceneEditorOverlay.PivotMode pivotMode = overlay.getPivotMode();
            ImGui.setNextItemWidth(80);
            if (ImGui.beginCombo("##Pivot", pivotMode.name())) {
                for (SceneEditorOverlay.PivotMode pm : SceneEditorOverlay.PivotMode.values()) {
                    if (ImGui.selectable(pm.name(), pm == pivotMode)) {
                        overlay.setPivotMode(pm);
                    }
                }
                ImGui.endCombo();
            }
        }

        ImGui.sameLine();
        ImGui.text(" | ");
        ImGui.sameLine();

        if (ImGui.button("Add...")) {
            ImGui.openPopup("scene_add_palette");
        }
        renderAddPalette(session);
    }

    private void renderAddPalette(SceneSessionManager session) {
        if (!ImGui.beginPopup("scene_add_palette")) {
            return;
        }
        if (ImGui.menuItem("Empty Group")) {
            overlay.spawnEmptyObject(session, null);
        }
        if (ImGui.beginMenu("Models")) {
            renderAssetMenu("model");
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Displays")) {
            renderAssetMenu("display");
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Lights")) {
            renderAssetMenu("light");
            ImGui.endMenu();
        }
        if (ImGui.beginMenu("Primitives")) {
            renderAssetMenu("primitive");
            ImGui.endMenu();
        }
        if (ImGui.menuItem("Particle Emitter")) {
            overlay.spawnParticleEmitter(session, null);
        }
        if (ImGui.menuItem("Marker")) {
            overlay.getMarkerNameBuffer().set(overlay.generateMarkerName());
            overlay.spawnMarker(session, null);
        }
        if (ImGui.menuItem("Zone")) {
            overlay.spawnZone(session, null);
        }
        if (ImGui.menuItem("Player Model")) {
            overlay.spawnPlayerModel(session, null);
        }
        if (ImGui.menuItem("Camera")) {
            overlay.resetCameraBuffers();
            overlay.spawnCamera(session, null);
        }
        ImGui.endPopup();
    }

    private void renderAssetMenu(String type) {
        List<MoudPackets.EditorAssetDefinition> assets = EditorAssetCatalog.getInstance().getAssets();
        int shown = 0;
        for (MoudPackets.EditorAssetDefinition asset : assets) {
            if (!type.equalsIgnoreCase(asset.objectType())) {
                continue;
            }
            if (ImGui.menuItem(asset.label())) {
                overlay.spawnAsset(asset);
            }
            shown++;
            if (shown >= 30) {
                ImGui.separator();
                ImGui.textDisabled("Use Asset Browser for more...");
                break;
            }
        }
        if (shown == 0) {
            ImGui.textDisabled("No assets available.");
        }
    }

    private void renderTransformModeButton(String label, int targetOp, int currentOp) {
        boolean isActive = (currentOp == targetOp);
        if (isActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.5f, 0.8f, 1.0f);
        }
        if (ImGui.button(label, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            overlay.setCurrentGizmoOperation(targetOp);
        }
        if (isActive) {
            ImGui.popStyleColor();
        }
    }

    private void renderModeButton(String label, int targetMode, int currentMode) {
        boolean isActive = (currentMode == targetMode);
        if (isActive) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.5f, 0.8f, 1.0f);
        }
        if (ImGui.button(label, 50, BUTTON_HEIGHT)) {
            overlay.setGizmoMode(targetMode);
        }
        if (isActive) {
            ImGui.popStyleColor();
        }
    }

    /**
     * Get the height of the scene toolbar.
     */
    public static float getToolbarHeight() {
        return TOOLBAR_HEIGHT;
    }
}
