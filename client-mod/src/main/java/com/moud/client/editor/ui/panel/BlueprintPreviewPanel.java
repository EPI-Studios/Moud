package com.moud.client.editor.ui.panel;

import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.scene.blueprint.BlueprintPreviewManager;
import com.moud.client.editor.ui.ImGuiTheme;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;


public final class BlueprintPreviewPanel {
    private final SceneEditorOverlay overlay;
    private final ImBoolean snapEnabled = new ImBoolean(false);
    private final float[] snapSize = new float[]{1.0f};
    private final ImBoolean mirrorX = new ImBoolean(false);
    private final ImBoolean mirrorZ = new ImBoolean(false);

    public BlueprintPreviewPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public boolean hasActivePreview() {
        return BlueprintPreviewManager.getInstance().getCurrent() != null;
    }

    public void render(SceneSessionManager session) {
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.INSPECTOR, "Blueprint Preview")) {
            ImGui.end();
            return;
        }

        BlueprintPreviewManager.PreviewState preview = BlueprintPreviewManager.getInstance().getCurrent();

        if (preview == null) {
            ImGui.textDisabled("No preview loaded");
            ImGui.end();
            return;
        }

        String previewName = overlay.getBlueprintPreviewNameBuffer().get();
        ImGui.textColored(0.7f, 0.9f, 1.0f, 1.0f, "PREVIEW: " + (previewName != null ? previewName : "unnamed"));
        ImGui.separator();
        ImGui.spacing();

        if (ImGui.button("Hide Preview", -1, 32)) {
            overlay.hideBlueprintPreview();
            ImGui.end();
            return;
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        boolean hasBlocks = preview.blueprint != null && preview.blueprint.blocks != null;
        float[] rotationBuffer = overlay.getPreviewRotationBuffer();
        float[] scaleBuffer = overlay.getPreviewScaleBuffer();

        ImGui.textColored(0.7f, 0.9f, 1.0f, 1.0f, "TRANSFORM");
        ImGui.separator();
        ImGui.spacing();

        float[] posBuffer = overlay.getPreviewPositionBuffer();
        ImGui.text("Position");
        ImGui.setNextItemWidth(-1);
        if (ImGui.dragFloat3("##pos", posBuffer, 0.1f)) {
            if (hasBlocks) {
                posBuffer[0] = Math.round(posBuffer[0]);
                posBuffer[1] = Math.round(posBuffer[1]);
                posBuffer[2] = Math.round(posBuffer[2]);
            } else if (snapEnabled.get() && snapSize[0] > 0) {
                posBuffer[0] = Math.round(posBuffer[0] / snapSize[0]) * snapSize[0];
                posBuffer[1] = Math.round(posBuffer[1] / snapSize[0]) * snapSize[0];
                posBuffer[2] = Math.round(posBuffer[2] / snapSize[0]) * snapSize[0];
            }
            BlueprintPreviewManager.getInstance().move(posBuffer);
            overlay.updatePreviewMatrix();
        }

        ImGui.spacing();

        if (hasBlocks) {
            rotationBuffer[0] = 0f;
            rotationBuffer[2] = 0f;

            ImGui.text("Rotation (Yaw)");
            float[] yawBuffer = new float[]{rotationBuffer[1]};
            ImGui.setNextItemWidth(-1);
            if (ImGui.dragFloat("##yaw", yawBuffer, 1f, -180f, 180f, "%.0f°")) {
                rotationBuffer[1] = Math.round(yawBuffer[0] / 90f) * 90f;
                BlueprintPreviewManager.getInstance().setRotation(0, rotationBuffer[1], 0);
                overlay.updatePreviewMatrix();
            }

            ImGui.spacing();

            ImGui.text("Mirror");
            boolean sx = scaleBuffer[0] < 0;
            boolean sz = scaleBuffer[2] < 0;
            mirrorX.set(sx);
            mirrorZ.set(sz);

            if (ImGui.checkbox("Mirror X", mirrorX)) {
                scaleBuffer[0] = mirrorX.get() ? -1f : 1f;
                BlueprintPreviewManager.getInstance().setScale(scaleBuffer[0], 1f, scaleBuffer[2]);
                overlay.updatePreviewMatrix();
            }

            ImGui.sameLine();

            if (ImGui.checkbox("Mirror Z", mirrorZ)) {
                scaleBuffer[2] = mirrorZ.get() ? -1f : 1f;
                BlueprintPreviewManager.getInstance().setScale(scaleBuffer[0], 1f, scaleBuffer[2]);
                overlay.updatePreviewMatrix();
            }

            scaleBuffer[1] = 1f;
            BlueprintPreviewManager.getInstance().setScale(scaleBuffer[0], 1f, scaleBuffer[2]);
        } else {
            ImGui.text("Rotation");
            ImGui.setNextItemWidth(-1);
            if (ImGui.dragFloat3("##rot", rotationBuffer, 1f, -180f, 180f, "%.0f°")) {
                BlueprintPreviewManager.getInstance().setRotation(rotationBuffer[0], rotationBuffer[1], rotationBuffer[2]);
                overlay.updatePreviewMatrix();
            }

            ImGui.spacing();

            ImGui.text("Scale");
            ImGui.setNextItemWidth(-60);
            if (ImGui.dragFloat3("##scale", scaleBuffer, 0.05f, 0.1f, 10f, "%.2f")) {
                BlueprintPreviewManager.getInstance().setScale(scaleBuffer[0], scaleBuffer[1], scaleBuffer[2]);
                overlay.updatePreviewMatrix();
            }
            ImGui.sameLine();
            if (ImGui.button("Reset", 50, 0)) {
                scaleBuffer[0] = scaleBuffer[1] = scaleBuffer[2] = 1f;
                BlueprintPreviewManager.getInstance().setScale(1, 1, 1);
                overlay.updatePreviewMatrix();
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.textColored(0.7f, 0.9f, 1.0f, 1.0f, "SNAPPING");
        ImGui.separator();
        ImGui.spacing();

        if (hasBlocks) {
            ImGui.textDisabled("Block blueprints always snap to grid");
        } else {
            ImGui.checkbox("Enable Grid Snap", snapEnabled);
            if (snapEnabled.get()) {
                ImGui.setNextItemWidth(-1);
                ImGui.dragFloat("Grid Size", snapSize, 0.1f, 0.1f, 10f, "%.1f");
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.textColored(0.7f, 0.9f, 1.0f, 1.0f, "GIZMO");
        ImGui.separator();
        ImGui.spacing();

        if (!overlay.isPreviewGizmoActive()) {
            if (ImGui.button("Enable Gizmo", -1, 32)) {
                overlay.setPreviewGizmoActive(true);
                overlay.updatePreviewMatrix();
            }
            ImGui.spacing();
            ImGui.textDisabled("Use gizmo to position in 3D view");
        } else {
            ImGuiTheme.pushWarningButtonStyle();
            if (ImGui.button("Disable Gizmo", -1, 32)) {
                overlay.setPreviewGizmoActive(false);
            }
            ImGuiTheme.popWarningButtonStyle();
            ImGui.spacing();
            ImGui.textColored(0.9f, 0.7f, 0.3f, 1.0f, "Gizmo active - manipulate in 3D view");
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        ImGui.textColored(0.7f, 0.9f, 1.0f, 1.0f, "ACTIONS");
        ImGui.separator();
        ImGui.spacing();

        ImGuiTheme.pushSuccessButtonStyle();
        if (ImGui.button("Place Blueprint", -1, 40)) {
            // TODO: Implement actual placement
            overlay.hideBlueprintPreview();
        }
        ImGuiTheme.popSuccessButtonStyle();

        ImGui.end();
    }
}
