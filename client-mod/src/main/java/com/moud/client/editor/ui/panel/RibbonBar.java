package com.moud.client.editor.ui.panel;

import com.moud.client.editor.assets.EditorAssetCatalog;
import com.moud.client.editor.plugin.EditorPluginHost;
import com.moud.client.editor.scene.SceneObject;
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
        ImGui.separator();
        ImGui.text("Usage: " + session.getSceneGraph().getObjects().size() + " objects");
    }

    private void renderModel(SceneSessionManager session) {
        if (ImGui.button("Add...")) {
            ImGui.openPopup("model_add_palette");
        }
        renderAddPalette(session);
        ImGui.sameLine();
        if (ImGui.button("Blueprint Tools")) {
            ImGui.openPopup("blueprint_tools_popup");
        }
        ImGui.separator();
        ImGui.textDisabled("Drag assets from the browser or explorer into the scene.");
        ImGui.textDisabled("Utilities");
        if (ImGui.button("Duplicate Selection")) {
            // TODO: implement multi-selection duplication
        }
        ImGui.sameLine();
        if (ImGui.button("Focus Selection")) {
            SceneObject selection = overlay.getSelectedObject();
            if (selection != null) {
                overlay.focusSelection(selection);
            }
        }
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

    private void renderAddPalette(SceneSessionManager session) {
        if (!ImGui.beginPopup("model_add_palette")) {
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
        if (ImGui.menuItem("Particle Emitter")) {
            overlay.spawnParticleEmitter(session, null);
        }
        if (ImGui.menuItem("Marker")) {
            overlay.getMarkerNameBuffer().set(overlay.generateMarkerName());
            overlay.spawnMarker(session, null);
        }
        if (ImGui.menuItem("Fake Player")) {
            overlay.spawnFakePlayer(session, null);
        }
        if (ImGui.menuItem("Camera")) {
            overlay.resetCameraBuffers();
            overlay.spawnCamera(session, null);
        }
        ImGui.endPopup();
    }

    private void renderAssetMenu(String type) {
        List<com.moud.network.MoudPackets.EditorAssetDefinition> assets = EditorAssetCatalog.getInstance().getAssets();
        int shown = 0;
        for (com.moud.network.MoudPackets.EditorAssetDefinition asset : assets) {
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
}
