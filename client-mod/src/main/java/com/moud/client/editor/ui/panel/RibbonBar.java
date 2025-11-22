package com.moud.client.editor.ui.panel;

import com.moud.client.editor.plugin.EditorPluginHost;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import imgui.ImGui;
import imgui.type.ImBoolean;

import java.util.List;


public final class RibbonBar {
    private final SceneEditorOverlay overlay;

    public RibbonBar(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void render(SceneSessionManager session) {
        if (!ImGui.beginTabBar("editor-ribbon-tabs")) {
            return;
        }
        if (ImGui.beginTabItem("Home")) {
            renderHome(session);
            ImGui.endTabItem();
        }
        if (ImGui.beginTabItem("Model")) {
            renderModel(session);
            ImGui.endTabItem();
        }
        if (ImGui.beginTabItem("View")) {
            renderView();
            ImGui.endTabItem();
        }
        if (ImGui.beginTabItem("Plugins")) {
            renderPlugins();
            ImGui.endTabItem();
        }
        ImGui.endTabBar();
    }

    private void renderHome(SceneSessionManager session) {
        if (ImGui.button("Sync Scene")) {
            session.forceRefresh();
        }
        ImGui.sameLine();
        boolean undoDisabled = !overlay.canUndo();
        if (undoDisabled) ImGui.beginDisabled();
        if (ImGui.button("Undo")) {
            overlay.undo();
        }
        if (undoDisabled) ImGui.endDisabled();
        ImGui.sameLine();
        boolean redoDisabled = !overlay.canRedo();
        if (redoDisabled) ImGui.beginDisabled();
        if (ImGui.button("Redo")) {
            overlay.redo();
        }
        if (redoDisabled) ImGui.endDisabled();
        ImGui.sameLine();
        boolean diagnosticsVisible = overlay.isDiagnosticsVisible();
        if (ImGui.button(diagnosticsVisible ? "Hide Diagnostics" : "Show Diagnostics")) {
            overlay.setDiagnosticsVisible(!diagnosticsVisible);
        }
        ImGui.separator();
        ImGui.text("Usage: " + session.getSceneGraph().getObjects().size() + " objects");
    }

    private void renderModel(SceneSessionManager session) {
        if (ImGui.button("New Empty")) {
            overlay.spawnEmptyObject(session, null);
        }
        ImGui.sameLine();
        boolean haveModels = overlay.hasAssetsOfType("model");
        if (!haveModels) ImGui.beginDisabled();
        if (ImGui.button("Add Model")) {
            ImGui.openPopup("hierarchy_add_model_popup");
        }
        if (!haveModels) ImGui.endDisabled();
        ImGui.sameLine();
        boolean haveDisplays = overlay.hasAssetsOfType("display");
        if (!haveDisplays) ImGui.beginDisabled();
        if (ImGui.button("Add Display")) {
            ImGui.openPopup("hierarchy_add_display_popup");
        }
        if (!haveDisplays) ImGui.endDisabled();
        ImGui.sameLine();
        boolean haveLights = overlay.hasAssetsOfType("light");
        if (!haveLights) ImGui.beginDisabled();
        if (ImGui.button("Add Light")) {
            ImGui.openPopup("hierarchy_add_light_popup");
        }
        if (!haveLights) ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.button("Add Marker")) {
            overlay.getMarkerNameBuffer().set(overlay.generateMarkerName());
            ImGui.openPopup("hierarchy_add_marker_popup");
        }
        ImGui.sameLine();
        if (ImGui.button("Blueprint Tools")) {
            ImGui.openPopup("blueprint_tools_popup");
        }
        ImGui.separator();
        ImGui.textDisabled("Drag assets from the browser or explorer into the scene.");
    }

    private void renderView() {
        ImBoolean explorerToggle = new ImBoolean(overlay.isExplorerVisible());
        if (ImGui.checkbox("Explorer", explorerToggle)) {
            overlay.setExplorerVisible(explorerToggle.get());
        }
        ImGui.sameLine();
        ImBoolean inspectorToggle = new ImBoolean(overlay.isInspectorVisible());
        if (ImGui.checkbox("Inspector", inspectorToggle)) {
            overlay.setInspectorVisible(inspectorToggle.get());
        }
        ImGui.sameLine();
        ImBoolean scriptToggle = new ImBoolean(overlay.isScriptViewerVisible());
        if (ImGui.checkbox("Script Viewer", scriptToggle)) {
            overlay.setScriptViewerVisible(scriptToggle.get());
        }
        ImGui.sameLine();
        ImBoolean assetToggle = new ImBoolean(overlay.isAssetBrowserVisible());
        if (ImGui.checkbox("Asset Browser", assetToggle)) {
            overlay.setAssetBrowserVisible(assetToggle.get());
        }
        ImGui.separator();
        ImGui.textDisabled("Viewport overlays");
        ImBoolean gizmoToggle = new ImBoolean(overlay.isGizmoToolbarVisible());
        if (ImGui.checkbox("Gizmo Controls Bar", gizmoToggle)) {
            overlay.setGizmoToolbarVisible(gizmoToggle.get());
        }
        ImGui.sameLine();
        ImBoolean boundsToggle = new ImBoolean(overlay.isSelectionBoundsVisible());
        if (ImGui.checkbox("Selection Bounds", boundsToggle)) {
            overlay.setSelectionBoundsVisible(boundsToggle.get());
        }
    }

    private void renderPlugins() {
        EditorPluginHost host = EditorPluginHost.getInstance();
        boolean rendered = host.renderRibbonTab("Plugins");
        List<EditorPluginHost.EditorPluginDescriptor> descriptors = host.getDescriptors();
        if (!descriptors.isEmpty()) {
            ImGui.textDisabled("Registered plugins");
            for (EditorPluginHost.EditorPluginDescriptor descriptor : descriptors) {
                ImGui.separator();
                ImGui.text(descriptor.displayName());
                ImGui.textDisabled(descriptor.id());
                ImGui.textWrapped(descriptor.description());
            }
        }
        if (!rendered && descriptors.isEmpty()) {
            ImGui.textDisabled("No editor plugins registered yet.");
        }
    }
}
