package com.moud.client.editor.ui.panel;

import com.moud.client.editor.scene.SceneEditorDiagnostics;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.scene.blueprint.BlueprintCornerSelector;
import com.moud.client.editor.scene.blueprint.BlueprintPreviewManager;
import com.moud.client.editor.scene.blueprint.ClientBlueprintNetwork;
import com.moud.client.editor.ui.ImGuiTheme;
import com.moud.client.editor.ui.SceneEditorOverlay;
import imgui.ImGui;
import imgui.extension.imguizmo.flag.Operation;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

public final class BlueprintToolsPanel {
    private static final long LIST_FETCH_COOLDOWN = 2000;

    private final SceneEditorOverlay overlay;
    private List<String> availableBlueprints = new ArrayList<>();
    private long lastListFetch = 0;
    private String selectedBlueprintName = null;

    private final ImBoolean snapEnabled = new ImBoolean(false);
    private final float[] snapSize = new float[]{1.0f};

    private final ImBoolean mirrorX = new ImBoolean(false);
    private final ImBoolean mirrorZ = new ImBoolean(false);

    private final ImBoolean browserExpanded = new ImBoolean(true);
    private final ImBoolean createExpanded = new ImBoolean(true);
    private final ImBoolean previewExpanded = new ImBoolean(true);

    public BlueprintToolsPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void renderTabContent(SceneSessionManager session) {
        if (renderSectionHeader("SAVED BLUEPRINTS", browserExpanded)) {
            ImGui.indent(4);
            renderBrowserSection(session);
            ImGui.unindent(4);
            ImGui.spacing();
        }

        ImGui.spacing();

        if (renderSectionHeader("CREATE BLUEPRINT", createExpanded)) {
            ImGui.indent(4);
            renderCreateSection(session);
            ImGui.unindent(4);
            ImGui.spacing();
        }

        ImGui.spacing();

        if (renderSectionHeader("PREVIEW & PLACE", previewExpanded)) {
            ImGui.indent(4);
            renderPreviewSection(session);
            ImGui.unindent(4);
        }
    }

    private boolean renderSectionHeader(String label, ImBoolean expanded) {
        ImGui.pushStyleColor(ImGuiCol.Header, 0.16f, 0.32f, 0.50f, 0.60f);
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.20f, 0.40f, 0.62f, 0.80f);
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.24f, 0.48f, 0.75f, 1.00f);

        int flags = ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.Framed | ImGuiTreeNodeFlags.SpanAvailWidth;
        boolean open = ImGui.collapsingHeader(label, flags);
        expanded.set(open);

        ImGui.popStyleColor(3);
        return open;
    }

    private void renderCreateSection(SceneSessionManager session) {
        ImGui.text("Region Selection");
        ImGui.spacing();

        float buttonWidth = (ImGui.getContentRegionAvailX() - 8) / 2;
        boolean pickingA = overlay.isCornerSelectionActive() && overlay.getPendingCorner() == BlueprintCornerSelector.Corner.A;
        boolean pickingB = overlay.isCornerSelectionActive() && overlay.getPendingCorner() == BlueprintCornerSelector.Corner.B;

        if (pickingA) ImGuiTheme.pushWarningButtonStyle();
        if (ImGui.button("Pick Corner A", buttonWidth, 28)) {
            overlay.beginCornerSelection(BlueprintCornerSelector.Corner.A);
        }
        if (pickingA) ImGuiTheme.popWarningButtonStyle();

        ImGui.sameLine();

        if (pickingB) ImGuiTheme.pushWarningButtonStyle();
        if (ImGui.button("Pick Corner B", buttonWidth, 28)) {
            overlay.beginCornerSelection(BlueprintCornerSelector.Corner.B);
        }
        if (pickingB) ImGuiTheme.popWarningButtonStyle();

        if (overlay.isCornerSelectionActive()) {
            ImGui.spacing();
            ImGui.pushStyleColor(ImGuiCol.Text, ImGuiTheme.WARNING_R, ImGuiTheme.WARNING_G, ImGuiTheme.WARNING_B, 1.0f);
            ImGui.text(">> Click in world to set corner " + overlay.getPendingCorner());
            ImGui.popStyleColor();
        }

        ImGui.spacing();

        if (overlay.isRegionACaptured()) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.12f, 0.18f, 0.14f, 1.0f);
            ImGui.dragFloat3("Corner A", overlay.getRegionCornerA(), 0.1f);
            ImGui.popStyleColor();
        } else {
            ImGui.textDisabled("Corner A: not set");
        }

        if (overlay.isRegionBCaptured()) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.12f, 0.14f, 0.18f, 1.0f);
            ImGui.dragFloat3("Corner B", overlay.getRegionCornerB(), 0.1f);
            ImGui.popStyleColor();
        } else {
            ImGui.textDisabled("Corner B: not set");
        }

        ImGui.spacing();

        Box regionBox = overlay.getSelectedRegionBox();
        if (regionBox != null) {
            double lenX = regionBox.maxX - regionBox.minX;
            double lenY = regionBox.maxY - regionBox.minY;
            double lenZ = regionBox.maxZ - regionBox.minZ;

            ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.08f, 0.10f, 0.12f, 1.0f);
            ImGui.beginChild("region_stats", 0, 54, true);
            ImGui.text("Size: %.1f x %.1f x %.1f".formatted(lenX, lenY, lenZ));
            int objectCount = overlay.countObjectsInRegion(regionBox, false);
            int markerCount = overlay.countObjectsInRegion(regionBox, true);
            ImGui.text("Contains: %d objects, %d markers".formatted(objectCount, markerCount));
            ImGui.endChild();
            ImGui.popStyleColor();

            ImGui.spacing();

            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 110);
            ImGui.inputTextWithHint("##name", "Blueprint name...", overlay.getBlueprintNameBuffer());
            ImGui.sameLine();

            ImGuiTheme.pushSuccessButtonStyle();
            if (ImGui.button("Export", 100, 0)) {
                overlay.exportBlueprint(session, regionBox, overlay.getBlueprintNameBuffer().get());
            }
            ImGuiTheme.popSuccessButtonStyle();

            ImGui.sameLine(0, 4);
            if (ImGui.button("Clear", 0, 0)) {
                overlay.clearCornerSelection();
            }
        } else {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.55f, 1.0f);
            ImGui.textWrapped("Pick two corners in the world to define the export region.");
            ImGui.popStyleColor();
        }
    }

    private void renderBrowserSection(SceneSessionManager session) {
        ImGuiTheme.pushAccentButtonStyle();
        if (ImGui.button("Refresh", 70, 24)) {
            fetchBlueprintList();
        }
        ImGuiTheme.popAccentButtonStyle();

        ImGui.sameLine();
        ImGui.textDisabled("(" + availableBlueprints.size() + " saved)");

        long now = System.currentTimeMillis();
        if (availableBlueprints.isEmpty() && now - lastListFetch > LIST_FETCH_COOLDOWN) {
            fetchBlueprintList();
        }

        ImGui.spacing();

        if (availableBlueprints.isEmpty()) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.55f, 1.0f);
            ImGui.textWrapped("No blueprints saved yet. Create one using the section below.");
            ImGui.popStyleColor();
        } else {
            ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.07f, 0.08f, 0.09f, 1.0f);
            ImGui.beginChild("blueprint_list", 0, 120, true);

            for (String name : availableBlueprints) {
                boolean isSelected = name.equals(selectedBlueprintName);

                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Header, ImGuiTheme.ACCENT_R * 0.4f, ImGuiTheme.ACCENT_G * 0.4f, ImGuiTheme.ACCENT_B * 0.4f, 1.0f);
                }

                if (ImGui.selectable("  " + name, isSelected)) {
                    selectedBlueprintName = name;
                    overlay.getBlueprintPreviewNameBuffer().set(name);
                    overlay.loadBlueprintPreview(name, overlay.getPreviewPositionBuffer());
                    resetTransformBuffers();
                }

                if (isSelected) {
                    ImGui.popStyleColor();
                }
            }

            ImGui.endChild();
            ImGui.popStyleColor();

            ImGui.spacing();

            if (selectedBlueprintName != null) {
                float buttonWidth = (ImGui.getContentRegionAvailX() - 8) / 2;

                ImGuiTheme.pushAccentButtonStyle();
                if (ImGui.button("Load Preview", buttonWidth, 26)) {
                    overlay.loadBlueprintPreview(selectedBlueprintName, overlay.getPreviewPositionBuffer());
                    resetTransformBuffers();
                }
                ImGuiTheme.popAccentButtonStyle();

                ImGui.sameLine();

                ImGuiTheme.pushDangerButtonStyle();
                if (ImGui.button("Delete", buttonWidth, 26)) {
                    String toDelete = selectedBlueprintName;
                    ClientBlueprintNetwork.getInstance().deleteBlueprint(toDelete, success -> {
                        if (success) {
                            availableBlueprints.remove(toDelete);
                            if (toDelete.equals(selectedBlueprintName)) {
                                selectedBlueprintName = null;
                            }
                        }
                    });
                }
                ImGuiTheme.popDangerButtonStyle();
            }
        }
    }

    private void fetchBlueprintList() {
        lastListFetch = System.currentTimeMillis();
        ClientBlueprintNetwork.getInstance().listBlueprints(list -> {
            availableBlueprints = new ArrayList<>(list);
        });
    }

    private void resetTransformBuffers() {
        float[] rotationBuffer = overlay.getPreviewRotationBuffer();
        float[] scaleBuffer = overlay.getPreviewScaleBuffer();
        if (rotationBuffer == null || scaleBuffer == null) {
            return;
        }
        rotationBuffer[0] = 0;
        rotationBuffer[1] = 0;
        rotationBuffer[2] = 0;
        scaleBuffer[0] = 1f;
        scaleBuffer[1] = 1f;
        scaleBuffer[2] = 1f;
        mirrorX.set(false);
        mirrorZ.set(false);
        BlueprintPreviewManager.getInstance().setRotation(0, 0, 0);
        BlueprintPreviewManager.getInstance().setScale(1, 1, 1);
        overlay.updatePreviewMatrix();
    }

    private void renderPreviewSection(SceneSessionManager session) {
        BlueprintPreviewManager.PreviewState preview = BlueprintPreviewManager.getInstance().getCurrent();
        float[] rotationBuffer = overlay.getPreviewRotationBuffer();
        float[] scaleBuffer = overlay.getPreviewScaleBuffer();

        if (preview == null) {
            ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.55f, 1.0f);
            ImGui.textWrapped("Select a blueprint above to preview and place it in the world.");
            ImGui.popStyleColor();

            ImGui.spacing();

            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 80);
            ImGui.inputTextWithHint("##preview_name", "Blueprint name...", overlay.getBlueprintPreviewNameBuffer());
            ImGui.sameLine();

            if (ImGui.button("Load", 70, 0)) {
                overlay.loadBlueprintPreview(overlay.getBlueprintPreviewNameBuffer().get(), overlay.getPreviewPositionBuffer());
                resetTransformBuffers();
            }

            overlay.setPreviewGizmoActive(false);
            return;
        }

        String previewName = overlay.getBlueprintPreviewNameBuffer().get();
        ImGui.text("Preview: " + (previewName != null ? previewName : "unnamed"));
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 50);
        if (ImGui.smallButton("Hide")) {
            overlay.hideBlueprintPreview();
            return;
        }

        ImGui.spacing();

        boolean hasBlocks = preview.blueprint != null && preview.blueprint.blocks != null;

        ImGui.pushStyleColor(ImGuiCol.ChildBg, 0.08f, 0.09f, 0.11f, 1.0f);
        ImGui.beginChild("transform_controls", 0, 140, true);

        ImGui.text("Transform");
        ImGui.spacing();

        float[] posBuffer = overlay.getPreviewPositionBuffer();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.dragFloat3("Position##prev", posBuffer, 0.1f)) {
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

        if (hasBlocks) {
            rotationBuffer[0] = 0f;
            rotationBuffer[2] = 0f;

            float[] yawBuffer = new float[]{rotationBuffer[1]};
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.dragFloat("Yaw##prev", yawBuffer, 1f, -180f, 180f, "%.0f")) {
                rotationBuffer[1] = Math.round(yawBuffer[0] / 90f) * 90f;
                BlueprintPreviewManager.getInstance().setRotation(0, rotationBuffer[1], 0);
                overlay.updatePreviewMatrix();
            }

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
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.dragFloat3("Rotation##prev", rotationBuffer, 1f, -180f, 180f, "%.0f")) {
                BlueprintPreviewManager.getInstance().setRotation(rotationBuffer[0], rotationBuffer[1], rotationBuffer[2]);
                overlay.updatePreviewMatrix();
            }

            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 55);
            if (ImGui.dragFloat3("Scale##prev", scaleBuffer, 0.05f, 0.1f, 10f, "%.2f")) {
                BlueprintPreviewManager.getInstance().setScale(scaleBuffer[0], scaleBuffer[1], scaleBuffer[2]);
                overlay.updatePreviewMatrix();
            }
            ImGui.sameLine();
            if (ImGui.smallButton("1:1")) {
                scaleBuffer[0] = scaleBuffer[1] = scaleBuffer[2] = 1f;
                BlueprintPreviewManager.getInstance().setScale(1, 1, 1);
                overlay.updatePreviewMatrix();
            }
        }

        ImGui.endChild();
        ImGui.popStyleColor();

        ImGui.spacing();

        ImGui.checkbox("Grid Snap", snapEnabled);
        if (snapEnabled.get()) {
            ImGui.sameLine();
            ImGui.setNextItemWidth(50);
            ImGui.dragFloat("##snap", snapSize, 0.1f, 0.1f, 10f, "%.1f");
        }

        ImGui.sameLine(ImGui.getContentRegionAvailX() - 100);
        if (!overlay.isPreviewGizmoActive()) {
            ImGuiTheme.pushAccentButtonStyle();
            if (ImGui.button("Gizmo", 95, 0)) {
                overlay.setPreviewGizmoActive(true);
                overlay.updatePreviewMatrix();
            }
            ImGuiTheme.popAccentButtonStyle();
        } else {
            ImGuiTheme.pushWarningButtonStyle();
            if (ImGui.button("Gizmo ON", 95, 0)) {
                overlay.setPreviewGizmoActive(false);
            }
            ImGuiTheme.popWarningButtonStyle();

            if (hasBlocks) {
                overlay.setPreviewGizmoOperation(Operation.TRANSLATE);
            } else {
                ImGui.spacing();
                if (ImGui.radioButton("Move", overlay.getPreviewGizmoOperation() == Operation.TRANSLATE)) {
                    overlay.setPreviewGizmoOperation(Operation.TRANSLATE);
                }
                ImGui.sameLine();
                if (ImGui.radioButton("Rotate", overlay.getPreviewGizmoOperation() == Operation.ROTATE)) {
                    overlay.setPreviewGizmoOperation(Operation.ROTATE);
                }
                ImGui.sameLine();
                if (ImGui.radioButton("Scale", overlay.getPreviewGizmoOperation() == Operation.SCALE)) {
                    overlay.setPreviewGizmoOperation(Operation.SCALE);
                }
            }

            overlay.renderPreviewGizmo();
        }

        ImGui.spacing();
        ImGui.spacing();

        if (previewName != null && !previewName.isBlank()) {
            ImGuiTheme.pushSuccessButtonStyle();
            float buttonHeight = 36;
            if (ImGui.button("PLACE IN WORLD", ImGui.getContentRegionAvailX(), buttonHeight)) {
                placeBlueprint(session, previewName, posBuffer, rotationBuffer, scaleBuffer);
            }
            ImGuiTheme.popSuccessButtonStyle();

            ImGui.spacing();
            ImGui.textDisabled("Will create " + preview.boxes.size() + " objects at the preview location.");
        }
    }

    private void placeBlueprint(SceneSessionManager session, String name, float[] position, float[] rotation, float[] scale) {
        String sceneId = session.getActiveSceneId();
        ClientBlueprintNetwork.getInstance().placeBlueprint(sceneId, name, position, rotation, scale, result -> {
            if (result.success()) {
                SceneEditorDiagnostics.log("Placed blueprint '" + name + "': " + result.createdObjectIds().size() + " objects");
                session.forceRefresh();
                overlay.hideBlueprintPreview();
            } else {
                SceneEditorDiagnostics.log("Failed to place blueprint: " + result.message());
            }
        });
    }
}