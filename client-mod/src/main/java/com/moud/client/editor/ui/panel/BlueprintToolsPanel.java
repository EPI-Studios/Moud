package com.moud.client.editor.ui.panel;

import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.scene.blueprint.BlueprintCornerSelector;
import com.moud.client.editor.scene.blueprint.BlueprintPreviewManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import imgui.ImGui;
import imgui.extension.imguizmo.flag.Operation;
import net.minecraft.util.math.Box;

public final class BlueprintToolsPanel {
    private final SceneEditorOverlay overlay;

    public BlueprintToolsPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void render(SceneSessionManager session) {
        if (!ImGui.beginPopup("blueprint_tools_popup")) {
            return;
        }
        ImGui.text("Blueprint Tools");
        ImGui.sameLine();
        if (ImGui.button("Close##blueprint_close")) {
            ImGui.closeCurrentPopup();
            ImGui.endPopup();
            return;
        }
        ImGui.separator();
        renderRegionSection();
        Box regionBox = overlay.getSelectedRegionBox();
        if (regionBox != null) {
            double lenX = regionBox.maxX - regionBox.minX;
            double lenY = regionBox.maxY - regionBox.minY;
            double lenZ = regionBox.maxZ - regionBox.minZ;
            ImGui.text("Size: %.2f x %.2f x %.2f".formatted(lenX, lenY, lenZ));
            int objectCount = overlay.countObjectsInRegion(regionBox, false);
            int markerCount = overlay.countObjectsInRegion(regionBox, true);
            ImGui.text("Objects: " + objectCount + "  Markers: " + markerCount);
            ImGui.inputText("Blueprint Name", overlay.getBlueprintNameBuffer());
            if (ImGui.button("Export Blueprint")) {
                overlay.exportBlueprint(session, regionBox, overlay.getBlueprintNameBuffer().get());
            }
        } else {
            ImGui.textDisabled("Select two corners to enable export.");
        }
        ImGui.separator();
        renderPreviewSection();
        ImGui.endPopup();
    }

    private void renderRegionSection() {
        ImGui.textDisabled("Region Selection");
        if (ImGui.button("Pick Corner A")) {
            overlay.beginCornerSelection(BlueprintCornerSelector.Corner.A);
        }
        ImGui.sameLine();
        if (ImGui.button("Pick Corner B")) {
            overlay.beginCornerSelection(BlueprintCornerSelector.Corner.B);
        }
        ImGui.sameLine();
        if (ImGui.button("Clear Region")) {
            overlay.clearCornerSelection();
        }
        if (overlay.isCornerSelectionActive()) {
            ImGui.sameLine();
            BlueprintCornerSelector.Corner pending = overlay.getPendingCorner();
            if (pending != null) {
                ImGui.textColored(1f, 0.85f, 0.35f, 1f, "Picking corner " + pending);
            }
        }
        if (overlay.isRegionACaptured()) {
            ImGui.dragFloat3("Corner A", overlay.getRegionCornerA(), 0.1f);
        } else {
            ImGui.textDisabled("Corner A not set");
        }
        if (overlay.isRegionBCaptured()) {
            ImGui.dragFloat3("Corner B", overlay.getRegionCornerB(), 0.1f);
        } else {
            ImGui.textDisabled("Corner B not set");
        }
    }

    private void renderPreviewSection() {
        ImGui.textDisabled("Preview");
        ImGui.inputTextWithHint("Blueprint File", "name without .json", overlay.getBlueprintPreviewNameBuffer());
        if (ImGui.button("Load Preview")) {
            overlay.loadBlueprintPreview(overlay.getBlueprintPreviewNameBuffer().get(), overlay.getPreviewPositionBuffer());
        }
        ImGui.sameLine();
        if (ImGui.button("Hide Preview")) {
            overlay.hideBlueprintPreview();
        }
        BlueprintPreviewManager.PreviewState preview = BlueprintPreviewManager.getInstance().getCurrent();
        if (preview != null) {
            if (ImGui.dragFloat3("Preview Position", overlay.getPreviewPositionBuffer(), 0.1f)) {
                BlueprintPreviewManager.getInstance().move(overlay.getPreviewPositionBuffer());
                overlay.updatePreviewMatrix();
            }
            if (!overlay.isPreviewGizmoActive()) {
                if (ImGui.button("Attach Preview Gizmo")) {
                    overlay.setPreviewGizmoActive(true);
                    overlay.updatePreviewMatrix();
                }
            } else {
                if (ImGui.button("Detach Preview Gizmo")) {
                    overlay.setPreviewGizmoActive(false);
                }
                if (ImGui.radioButton("Move", overlay.getPreviewGizmoOperation() == Operation.TRANSLATE)) {
                    overlay.setPreviewGizmoOperation(Operation.TRANSLATE);
                }
                ImGui.sameLine();
                if (ImGui.radioButton("Rotate", overlay.getPreviewGizmoOperation() == Operation.ROTATE)) {
                    overlay.setPreviewGizmoOperation(Operation.ROTATE);
                }
            }
            if (overlay.isPreviewGizmoActive()) {
                overlay.renderPreviewGizmo();
            }
        } else {
            ImGui.textDisabled("No preview loaded.");
            overlay.setPreviewGizmoActive(false);
        }
    }
}
