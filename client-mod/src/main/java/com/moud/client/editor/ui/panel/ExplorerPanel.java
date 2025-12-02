package com.moud.client.editor.ui.panel;

import com.moud.client.editor.assets.ProjectFileIndex;
import com.moud.client.editor.scene.SceneEditorDiagnostics;
import com.moud.client.editor.scene.SceneObject;
import com.moud.client.editor.scene.SceneSessionManager;
import com.moud.client.editor.ui.SceneEditorOverlay;
import com.moud.client.editor.ui.layout.EditorDockingLayout;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.util.List;
import java.util.Locale;
import java.util.Map;


public final class ExplorerPanel {
    private final SceneEditorOverlay overlay;
    private final ImString hierarchyFilter = new ImString(64);
    private final ImString runtimeFilter = new ImString(64);
    private final ImString projectFileFilter = new ImString(64);

    public ExplorerPanel(SceneEditorOverlay overlay) {
        this.overlay = overlay;
    }

    public void render(SceneSessionManager session) {
        if (!overlay.beginDockedPanel(EditorDockingLayout.Region.EXPLORER, "Explorer")) {
            ImGui.end();
            return;
        }
        renderToolbar(session);
        ImGui.separator();
        if (ImGui.beginTabBar("explorer-tabs")) {
            if (ImGui.beginTabItem("Scene")) {
                renderSceneTab(session);
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Files")) {
                renderFilesTab();
                ImGui.endTabItem();
            }
            if (ImGui.beginTabItem("Runtime")) {
                renderRuntimeTab();
                ImGui.endTabItem();
            }
            ImGui.endTabBar();
        }
        ImGui.end();
    }

    private void renderToolbar(SceneSessionManager session) {
        if (ImGui.button("Refresh Scene")) {
            session.forceRefresh();
        }
        ImGui.sameLine();
        if (ImGui.button("Refresh Project Files")) {
            ProjectFileIndex.getInstance().forceRefresh();
        }
        ImGui.sameLine();
        ImGui.textDisabled("Drag assets/files into the tree to parent entities.");
    }

    private void renderSceneTab(SceneSessionManager session) {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##hierarchy_search", "Search scene...", hierarchyFilter);
        ImGui.beginChild("hierarchy-scroll", 0, ImGui.getContentRegionAvailY(), true);
        Map<String, List<SceneObject>> tree = overlay.getCachedHierarchy(session);
        List<SceneObject> roots = tree.getOrDefault("", List.of());
        String filter = hierarchyFilter.get().trim().toLowerCase(Locale.ROOT);
        for (SceneObject root : roots) {
            overlay.renderHierarchyNode(session, root, tree, filter);
        }
        if (ImGui.beginDragDropTarget()) {
            overlay.handleHierarchyDrop(null);
            ImGui.endDragDropTarget();
        }
        ImGui.endChild();
    }

    private void renderFilesTab() {
        ProjectFileIndex index = ProjectFileIndex.getInstance();
        index.requestSyncIfNeeded();
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##project_file_search", "Search project files...", projectFileFilter);
        ImGui.beginChild("project-map-scroll", 0, ImGui.getContentRegionAvailY(), true);
        List<ProjectFileIndex.Node> roots = index.getRoots();
        if (roots.isEmpty()) {
            ImGui.textWrapped("Waiting for the server to stream /assets and /src. Trigger a project scan once available.");
        } else {
            String filter = projectFileFilter.get().toLowerCase(Locale.ROOT);
            for (ProjectFileIndex.Node node : roots) {
                renderProjectNode(node, filter);
            }
        }
        ImGui.endChild();
    }

    private void renderProjectNode(ProjectFileIndex.Node node, String filter) {
        boolean matches = filter.isBlank() || node.name().toLowerCase(Locale.ROOT).contains(filter);
        if (node.isDirectory()) {
            int flags = ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.OpenOnArrow;
            boolean open = ImGui.treeNodeEx(node.path(), flags, node.name());
            if (open) {
                for (ProjectFileIndex.Node child : node.children()) {
                    renderProjectNode(child, filter);
                }
                ImGui.treePop();
            }
        } else if (matches) {
            if (ImGui.selectable(node.name() + "##proj_" + node.path())) {
                SceneEditorDiagnostics.log("Selected file " + node.path());
            }
            overlay.emitProjectFileDragPayload(node.path());
        }
    }

    private void renderRuntimeTab() {
        ImGui.setNextItemWidth(-1);
        ImGui.inputTextWithHint("##runtime_search", "Search runtime objects...", runtimeFilter);
        ImGui.beginChild("runtime-scroll", 0, ImGui.getContentRegionAvailY(), true);
        overlay.renderRuntimeList(runtimeFilter.get());
        ImGui.endChild();
    }
}
