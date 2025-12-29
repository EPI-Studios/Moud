package com.moud.client.editor.ui.panel;

import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.scene.blueprint.BlueprintCornerSelector;
import com.moud.client.editor.scene.blueprint.BlueprintPreviewManager;
import com.moud.client.editor.ui.ImGuiTheme;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.type.ImBoolean;
import imgui.type.ImString;
import net.minecraft.util.math.Box;

import java.util.Objects;

public final class BlueprintCreatePanel {

    private static final String PANEL_TITLE = "Blueprint Tools";
    private static final String TAB_CREATE = "Create";
    private static final String TAB_PREVIEW = "Preview & Place";

    private final SceneEditorOverlay overlay;

    private final ImString blueprintName = new ImString(64);
    private final ImBoolean snapEnabled = new ImBoolean(false);
    private final float[] snapSize = new float[]{1.0f};
    private final ImBoolean mirrorX = new ImBoolean(false);
    private final ImBoolean mirrorZ = new ImBoolean(false);

    public BlueprintCreatePanel(SceneEditorOverlay overlay) {
        this.overlay = Objects.requireNonNull(overlay, "Overlay must not be null");
    }

    public void render(SceneSessionManager session) {
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.ASSET_BROWSER, PANEL_TITLE)) {
            ImGui.end();
            return;
        }

        if (ImGui.beginTabBar("##blueprint_tools_tabs")) {
            if (ImGui.beginTabItem(TAB_CREATE)) {
                renderCreateTab(session);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem(TAB_PREVIEW)) {
                renderPreviewTab(session);
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }

        ImGui.end();
    }

    private void renderCreateTab(SceneSessionManager session) {
        renderSectionHeader("REGION SELECTION");

        float availWidth = ImGui.getContentRegionAvailX();
        float buttonWidth = (availWidth - 8) / 2;

        boolean pickingA = isPicking(BlueprintCornerSelector.Corner.A);
        boolean pickingB = isPicking(BlueprintCornerSelector.Corner.B);

        renderCornerButton("Pick Corner A", BlueprintCornerSelector.Corner.A, pickingA, buttonWidth);
        ImGui.sameLine();
        renderCornerButton("Pick Corner B", BlueprintCornerSelector.Corner.B, pickingB, buttonWidth);

        if (overlay.isCornerSelectionActive()) {
            ImGui.spacing();
            ImGui.pushStyleColor(ImGuiCol.Text, ImGuiTheme.WARNING_R, ImGuiTheme.WARNING_G, ImGuiTheme.WARNING_B, 1.0f);
            ImGui.textWrapped(">> Click in the world to set corner " + overlay.getPendingCorner());
            ImGui.popStyleColor();
            ImGui.spacing();
        }

        ImGui.spacing();

        renderCoordinates("Corner A:", overlay.getRegionCornerA(), overlay.isRegionACaptured(), buttonWidth);
        ImGui.sameLine();
        renderCoordinates("Corner B:", overlay.getRegionCornerB(), overlay.isRegionBCaptured(), buttonWidth);

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        renderSectionHeader("SAVE BLUEPRINT");

        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##blueprint_name", "Blueprint name...", blueprintName);

        ImGui.spacing();

        boolean validCaptureState = overlay.isRegionACaptured()
                && overlay.isRegionBCaptured()
                && !blueprintName.get().trim().isEmpty();

        if (!validCaptureState) {
            ImGui.beginDisabled();
        }

        if (ImGui.button("Capture & Save Blueprint", -1, 36)) {
            performCapture(session);
        }

        if (!validCaptureState) {
            ImGui.endDisabled();
            ImGui.spacing();
            ImGui.textDisabled("Set both corners and enter a name to capture.");
        }

        ImGui.spacing();
        if (ImGui.button("Clear Selection", -1, 0)) {
            overlay.clearCornerSelection();
        }
    }

    private void performCapture(SceneSessionManager session) {
        Box regionBox = overlay.getSelectedRegionBox();
        if (regionBox != null) {
            overlay.exportBlueprint(session, regionBox, blueprintName.get().trim());
            blueprintName.set("");
            overlay.clearCornerSelection();
        }
    }

    private void renderCornerButton(String label, BlueprintCornerSelector.Corner corner, boolean active, float width) {
        if (active) ImGuiTheme.pushWarningButtonStyle();
        if (ImGui.button(label, width, 32)) {
            overlay.beginCornerSelection(corner);
        }
        if (active) ImGuiTheme.popWarningButtonStyle();
    }

    private void renderCoordinates(String label, float[] coords, boolean captured, float width) {
        ImGui.beginGroup();
        ImGui.text(label);
        if (captured) {
            ImGui.pushStyleColor(ImGuiCol.FrameBg, 0.12f, 0.24f, 0.18f, 1.0f);
            ImGui.setNextItemWidth(width);
            ImGui.dragFloat3("##" + label, coords, 0.1f);
            ImGui.popStyleColor();
        } else {
            ImGui.textDisabled("Not set");
        }
        ImGui.endGroup();
    }

    private boolean isPicking(BlueprintCornerSelector.Corner corner) {
        return overlay.isCornerSelectionActive() && overlay.getPendingCorner() == corner;
    }

    private void renderPreviewTab(SceneSessionManager session) {
        ImGui.spacing();

        if (BlueprintPreviewManager.getInstance().getCurrent() == null) {
            ImGui.textDisabled("No blueprint preview loaded.");
            ImGui.spacing();
            ImGui.textWrapped("Select a blueprint from the browser to preview it.");
            return;
        }

        renderSectionHeader("PREVIEW LOADED");
        ImGui.textWrapped("Use the gizmo to position and rotate.");

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        if (ImGui.checkbox("Grid Snap", snapEnabled)) {
        }

        if (snapEnabled.get()) {
            ImGui.sameLine();
            ImGui.setNextItemWidth(80);
            ImGui.dragFloat("##snap_size", snapSize, 0.1f, 0.1f, 10.0f, "%.1f");
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        renderSectionHeader("MIRROR");

        if (ImGui.checkbox("Mirror X", mirrorX)) {
        }
        ImGui.sameLine(0, 40);
        if (ImGui.checkbox("Mirror Z", mirrorZ)) {
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        renderSectionHeader("ACTIONS");

        float buttonWidth = (ImGui.getContentRegionAvailX() - 8) / 2;

        if (ImGui.button("Place Blueprint", buttonWidth, 36)) {
            overlay.hideBlueprintPreview();
        }

        ImGui.sameLine();

        if (ImGui.button("Cancel Preview", buttonWidth, 36)) {
            overlay.hideBlueprintPreview();
        }

        ImGui.spacing();
        ImGui.textDisabled("Position preview, then click Place.");
    }

    private void renderSectionHeader(String title) {
        ImGui.textColored(0.7f, 0.9f, 1.0f, 1.0f, title);
        ImGui.separator();
        ImGui.spacing();
    }
}